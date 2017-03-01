package com.ethercamp.contrdata.storage.dictionary;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.datasource.Source;
import org.ethereum.datasource.inmem.HashMapDB;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.ContractDetails;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.util.Utils;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.ethercamp.contrdata.storage.dictionary.GuessUtils.guessValue;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY;
import static org.apache.commons.lang3.ArrayUtils.*;
import static org.apache.commons.lang3.StringUtils.repeat;
import static org.apache.commons.lang3.StringUtils.substring;
import static org.ethereum.util.ByteUtil.*;
import static org.spongycastle.util.encoders.Hex.toHexString;

/**
 * Keep the information for mapping of the contract storage hashKeys into a meaningful structures
 * representing storageIndexes, arrays indexes, offsets and mapping keys.
 * All the hashkeys used in the contract are organized into a tree structure according to their corresponding
 * structures in the source language.
 * Taking the following Solidity members for example:
 * <code>
 * int a0;
 * int a1[];
 * mapping(int => int[]) a2;
 * </code>
 * The execution of the code
 * <code>
 * a1[333] = 1;
 * a2['MyKey'][220] = 1;
 * a2['MyKey'][221] = 1;
 * a2['AnotherKey'][1] = 1;
 * </code>
 * would result in the following tree:
 * <pre>
 *     .1
 *       [333]
 *     .2
 *       ('MyKey')
 *         [220]
 *         [221]
 *       ('AnotherKey')
 *         [1]
 * </pre>
 * Each store entry contains storage hashkey which might be used to obtain the actual value from the contract storage
 * <p>
 * The tree is stored compacted.
 * Compacted means that the elements which have children with only a single
 * child with type Offset and key '0' can be compacted: the meaningless
 * children are removed from the hierarchy.
 * E.g. the following subtree:
 * <pre>
 *     .1
 *       ('aaa')
 *          +0
 *            [0]
 *            [1]
 *       ('bbb)
 *          +0
 *            [0]
 * </pre>
 * would be compacted to
 * <pre>
 *     .1
 *       ('aaa')
 *         [0]
 *         [1]
 *       ('bbb)
 *         [0]
 * </pre>
 * <p>
 * If it appears that the subtree shouldn't be compacted i.e. a new child appears
 * <pre>
 *     .1
 *       ('aaa')
 *          +1
 * </pre>
 * the subtree is 'decompacted' i.e. all '+0' children are returned back
 * <p>
 * Created by Anton Nashatyrev on 09.09.2015.
 */
public class StorageDictionary {

    private static final int MAX_CHILDREN_TO_SORT = 100;
    private static final boolean SORT_MAP_KEYS = false;

    //    class ByteArraySerializer implements JsonSerializer<byte[]>

    /**
     * Class represents a tree element
     * All leaf elements represent the actual store slots and thus have a hashKey
     * Non-leaf elements (such as dynamic arrays) can also have a hashKey since e.g. the
     * array length (in Solidity) is stored in the slot which represents an array
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE,
            fieldVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class PathElement implements Comparable<PathElement> {

        public enum Type {
            Root,
            StorageIndex,  // top-level Contract field index
            Offset,   // Either Offset in struct, or index in static array or both combined
            ArrayIndex,  // dynamic array index
            MapKey   // the key of the 'mapping'
        }

        @Setter
        private StorageDictionary dictionary;

        @JsonProperty
        public Type type;
        @JsonProperty
        public String key;
        @JsonProperty
        public byte[] storageKey;

        // null means undefined yet
        // true means children were compacted
        // false means children were decompacted
        @JsonProperty
        public Boolean childrenCompacted = null;
        @JsonProperty
        public int childrenCount = 0;
        @JsonProperty
        public byte[] parentHash;
        @JsonProperty
        public byte[] nextSiblingHash;
        @JsonProperty
        public byte[] firstChildHash;
        @JsonProperty
        public byte[] lastChildHash;

        public PathElement() {
        }

        public PathElement(Type type, String key, byte[] storageKey) {
            this.type = type;
            this.key = key;
            this.storageKey = storageKey;
        }

        // using some 'random' hash for root since storageKey '0' is used
        private static final byte[] rootHash = Hex.decode("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
        private static final String NO_OFFSET_KEY = "0";

        private static PathElement createRoot() {
            return new PathElement(Type.Root, NO_OFFSET_KEY, rootHash);
        }

        public PathElement(Type type, int indexOffset, byte[] storageKey) {
            this(type, String.valueOf(indexOffset), storageKey);
        }

        public static PathElement createMapKey(String key, byte[] storageKey) {
            return new PathElement(Type.MapKey, key, storageKey);
        }

        public static PathElement createMapKey(int key, byte[] storageKey) {
            return createMapKey(String.valueOf(key), storageKey);
        }

        @Override
        public PathElement clone() {
            PathElement result = new PathElement();
            result.dictionary = dictionary;
            result.type = type;
            result.key = key;
            result.storageKey = storageKey;
            result.childrenCompacted = childrenCompacted;
            result.childrenCount = childrenCount;
            result.parentHash = parentHash;
            result.nextSiblingHash = nextSiblingHash;
            result.firstChildHash = firstChildHash;
            result.lastChildHash = lastChildHash;
            return result;
        }

        public boolean is(Type type) {
            return this.type == type;
        }

        public PathElement getParent() {
            return dictionary.get(parentHash);
        }

        public PathElement getFirstChild() {
            return dictionary.get(firstChildHash);
        }

        public PathElement getLastChild() {
            return dictionary.get(lastChildHash);
        }

        public PathElement getNextSibling() {
            return dictionary.get(nextSiblingHash);
        }

        public boolean hasChild(PathElement pathElement) {
            return (pathElement != null) && Arrays.equals(storageKey, pathElement.parentHash);
        }

        public PathElement addChild(PathElement newChild) {
            PathElement existingChild = dictionary.get(newChild.storageKey);
            if (hasChild(existingChild)) {
                return existingChild;
            }

            if (childrenCount > MAX_CHILDREN_TO_SORT || (!SORT_MAP_KEYS && newChild.is(Type.MapKey))) {
                // no more sorting just add to the end
                insertChild(getLastChild(), newChild);
            } else {
                PathElement insertAfter = null;
                Iterator<PathElement> chIt = getChildrenIterator();

                List<PathElement> prevs = new ArrayList<>();
                boolean hasLoop = false;

                while (chIt.hasNext()) {
                    PathElement next = chIt.next();
                    for (PathElement prev : prevs) {
                        hasLoop = (prev.compareTo(next) == 0);
                        if (hasLoop) break;
                    }
                    if (hasLoop) break;
                    prevs.add(next);

                    if (newChild.compareTo(next) < 0) {
                        break;
                    }
                    insertAfter = next;
                }
                insertChild(insertAfter, newChild);
            }

            return newChild;
        }

        public PathElement insertChild(PathElement insertAfter, PathElement newChild) {
            if (insertAfter == null) {
                // first element
                newChild.nextSiblingHash = firstChildHash;
                firstChildHash = newChild.storageKey;
                if (childrenCount == 0) {
                    lastChildHash = firstChildHash;
                }
            } else if (insertAfter.nextSiblingHash == null) {
                // last element
                insertAfter.nextSiblingHash = newChild.storageKey;
                insertAfter.invalidate();
                lastChildHash = newChild.storageKey;
            } else {
                newChild.nextSiblingHash = insertAfter.nextSiblingHash;
                insertAfter.nextSiblingHash = newChild.storageKey;
                insertAfter.invalidate();
            }

            newChild.parentHash = this.storageKey;
            dictionary.put(newChild);
            newChild.invalidate();
            childrenCount++;
            this.invalidate();

            return newChild;
        }

        public void addChildPath(PathElement[] pathElements) {
            if (pathElements.length == 0) return;

            boolean addCompacted;
            if (pathElements.length > 1 && pathElements[1].canBeCompactedWithParent()) {
                // this one particular path we are adding can be compacted
                if (childrenCompacted == Boolean.FALSE) {
                    addCompacted = false;
                } else {
                    childrenCompacted = Boolean.TRUE;
                    addCompacted = true;
                }
            } else {
                addCompacted = false;
                if (childrenCompacted == Boolean.TRUE) {
                    childrenCompacted = Boolean.FALSE;
                    // we already added compacted children - need to decompact them now
                    decompactAllChildren();
                } else {
                    childrenCompacted = Boolean.FALSE;
                }
            }

            if (addCompacted) {
                PathElement compacted = compactPath(pathElements[0], pathElements[1]);
                PathElement child = addChild(compacted);
                child.addChildPath(Arrays.copyOfRange(pathElements, 2, pathElements.length));
                dictionary.put(compacted);
            } else {
                PathElement child = addChild(pathElements[0]);
                child.addChildPath(Arrays.copyOfRange(pathElements, 1, pathElements.length));
            }
        }

        private static PathElement compactPath(PathElement parent, PathElement child) {
            return new PathElement(parent.type, parent.key, child.storageKey);
        }

        private PathElement[] decompactElement(PathElement pe) {
            PathElement parent = new PathElement(pe.type, pe.key, toVirtualStorageKey(pe.storageKey));
            dictionary.put(parent);

            PathElement child = new PathElement(Type.Offset, NO_OFFSET_KEY, pe.storageKey);
            child.childrenCount = pe.childrenCount;
            child.firstChildHash = pe.firstChildHash;
            child.lastChildHash = pe.lastChildHash;
            dictionary.put(child);

            return new PathElement[]{parent, child};
        }

        private void decompactAllChildren() {
            PathElement child = getFirstChild();
            removeAllChildren();
            while (child != null) {
                addChildPath(decompactElement(child));
                child = child.getNextSibling();
            }
        }

        private void removeAllChildren() {
            childrenCount = 0;
            firstChildHash = null;
            lastChildHash = null;
        }

        public static byte[] toVirtualStorageKey(byte[] childStorageKey) {
            BigInteger i = bytesToBigInteger(childStorageKey).subtract(BigInteger.ONE);
            return bigIntegerToBytes(i, 32);
        }

        private boolean canBeCompactedWithParent() {
            return is(Type.Offset) && "0".equals(key);
        }

        public Iterator<PathElement> getChildrenIterator() {
            return new Iterator<PathElement>() {

                private PathElement cur = getFirstChild();

                @Override
                public boolean hasNext() {
                    return cur != null;
                }

                @Override
                public PathElement next() {
                    PathElement ret = cur;
                    cur = cur.getNextSibling();
                    return ret;
                }
            };
        }

        public Stream<PathElement> getChildrenStream() {
            return StreamSupport.stream(getChildren().spliterator(), false);
        }

        public Iterable<PathElement> getChildren() {
            return () -> getChildrenIterator();
        }

        public List<PathElement> getChildren(int offset, int count) {
            List<PathElement> result = new ArrayList<>();

            int i = 0;
            for (PathElement child : getChildren()) {
                if (offset <= i++) {
                    result.add(child);
                }
                if (result.size() == count) {
                    return result;
                }
            }

            return result;
        }

        public PathElement findChildByKey(String key) {
            Iterator<PathElement> children = getChildrenIterator();
            while (children.hasNext()) {
                PathElement child = children.next();
                if (StringUtils.equals(child.key, key)) {
                    return child;
                }
            }

            return null;
        }

        public boolean hasChildren() {
            return getChildrenCount() > 0;
        }

        public byte[] getHash() {
            return storageKey;
        }

        private void invalidate() {
            dictionary.dirtyNodes.add(this);
        }

        public int getChildrenCount() {
            return childrenCount;
        }

        public String[] getFullPath() {
            return is(Type.Root)
                    ? EMPTY_STRING_ARRAY
                    : Utils.mergeArrays(getParent().getFullPath(), new String[]{key});
        }

        @Override
        public int compareTo(PathElement o) {
            if (type != o.type) return type.compareTo(o.type);
            if (is(Type.Offset) || is(Type.StorageIndex) || is(Type.ArrayIndex)) {
                try {
                    return new BigInteger(key, 16).compareTo(new BigInteger(o.key, 16));
                } catch (NumberFormatException e) {
                    // fallback to string compare
                }
            }
            return key.compareTo(o.key);
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + (key != null ? key.hashCode() : 0);
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PathElement that = (PathElement) o;
            if (type != that.type) return false;
            return !(key != null ? !key.equals(that.key) : that.key != null);

        }

        private static String shortHash(byte[] hash) {
            return isEmpty(hash) ? StringUtils.EMPTY : substring(toHexString(hash), 0, 8);
        }

        public String dump() {
            return toString() +
                    "(storageKey=" + shortHash(storageKey) + ", " +
                    "childCount=" + childrenCount + ", " +
                    "childrenCompacted=" + childrenCompacted + ", " +
                    "parentHash=" + shortHash(parentHash) + ", " +
                    "firstChildHash=" + shortHash(firstChildHash) + ", " +
                    "lastChildHash=" + shortHash(lastChildHash) + ", " +
                    "nextSiblingHash=" + shortHash(nextSiblingHash) + ")";
        }

        @Override
        public String toString() {
            switch (type) {
                case Root:
                    return "ROOT";
                case StorageIndex:
                    return "." + key;
                case Offset:
                    return "+" + key;
                case MapKey:
                    return "('" + key + "')";
                default:
                    return "[" + key + "]";
            }
        }

        public String toString(ContractDetails storage, int indent) {
            String s = (isEmpty(storageKey) ? repeat(" ", 64) : toHexString(storageKey)) + " : " + repeat("  ", indent) + this;

            if (storageKey != null && storage != null) {
                DataWord data = storage.get(new DataWord(storageKey));
                s += " = " + (data == null ? "<null>" : guessValue(data.getData()));
            }

            s += "\n";
            if (getChildrenCount() > 0) {
                int limit = 50;
                for (PathElement child : getChildren()) {
                    s += child.toString(storage, indent + 1);
                    if (limit-- <= 0) {
                        s += "\n             [Total: " + getChildrenCount() + " Rest skipped]\n";
                        break;
                    }
                }
            }

            return s;
        }

        public byte[] serialize() {
            return RLP.encodeList(
                    RLP.encodeInt(type.ordinal()),
                    RLP.encodeString(key),
                    RLP.encodeElement(nullToEmpty(storageKey)),
                    RLP.encodeElement(childrenCompacted == null ? EMPTY_BYTE_ARRAY : (childrenCompacted ? new byte[]{1} : new byte[]{0})),
                    RLP.encodeInt(childrenCount),
                    RLP.encodeElement(nullToEmpty(parentHash)),
                    RLP.encodeElement(nullToEmpty(nextSiblingHash)),
                    RLP.encodeElement(nullToEmpty(firstChildHash)),
                    RLP.encodeElement(nullToEmpty(lastChildHash))
            );
        }

        public static PathElement deserialize(byte[] bytes) {
            PathElement result = new PathElement();

            RLPList list = (RLPList) RLP.decode2(bytes).get(0);
            result.type = Type.values()[byteArrayToInt(list.get(0).getRLPData())];
            result.key = new String(list.get(1).getRLPData());
            result.storageKey = list.get(2).getRLPData();
            byte[] compB = list.get(3).getRLPData();
            result.childrenCompacted = compB == null ? null : (compB[0] == 0 ? Boolean.FALSE : Boolean.TRUE);
            result.childrenCount = byteArrayToInt(list.get(4).getRLPData());
            result.parentHash = list.get(5).getRLPData();
            result.nextSiblingHash = list.get(6).getRLPData();
            result.firstChildHash = list.get(7).getRLPData();
            result.lastChildHash = list.get(8).getRLPData();

            return result;
        }

        PathElement copyLight() {
            PathElement ret = new PathElement();
            ret.type = type;
            ret.key = key;
            ret.storageKey = storageKey;
            return ret;
        }
    }

    public static PathElement[] emptyPathElements() {
        return pathElements();
    }

    public static PathElement[] pathElements(PathElement... elements) {
        return elements;
    }


    private PathElement get(byte[] hash) {
        if (hash == null) return null;
        PathElement ret = cache.get(new ByteArrayWrapper(hash));
        if (ret == null) {
            ret = load(hash);
            if (ret != null) {
                put(ret);
            }
        }
        return ret;
    }

    private void put(PathElement pe) {
        cache.put(new ByteArrayWrapper(pe.storageKey), pe);
        pe.dictionary = this;
    }

    public PathElement load(byte[] hash) {
        PathElement element = null;

        byte[] bytes = storageDb.get(hash);
        if (isNotEmpty(bytes)) {
            element = PathElement.deserialize(bytes);
            element.setDictionary(this);
        }

        return element;
    }

    public void store() {
        dirtyNodes.stream().forEach(node -> storageDb.put(node.getHash(), node.serialize()));
        dirtyNodes.clear();
    }

    public StorageDictionary getFiltered(Set<DataWord> hashFilter) {
        StorageDictionary result = new StorageDictionary(new HashMapDB());

        for (DataWord hash : hashFilter) {
            List<PathElement> path = new ArrayList<>();
            PathElement pathElement = get(hash.getData());
            if (pathElement == null) continue;

            while (!pathElement.is(PathElement.Type.Root)) {
                path.add(0, pathElement.copyLight());
                pathElement = pathElement.getParent();
            }

            result.addPath(path.stream().toArray(PathElement[]::new));
        }

        return result;
    }

    @Getter
    private Source<byte[], byte[]> storageDb;
    private PathElement root;
    private boolean exist;

    private Map<ByteArrayWrapper, PathElement> cache = new HashMap<>();
    private List<PathElement> dirtyNodes = new ArrayList<>();

    public StorageDictionary(Source<byte[], byte[]> storageDb) {
        this.storageDb = storageDb;
        this.root = load(PathElement.rootHash);
        this.exist = (root != null);

        if (!this.exist) {
            this.root = PathElement.createRoot();
            put(this.root);
        }
    }

    public boolean isExist() {
        return exist;
    }

    public boolean hasChanges() {
        return !dirtyNodes.isEmpty();
    }

    public synchronized void addPath(PathElement[] path) {
        int startIdx = path.length - 1;
        PathElement existingPE = null;
        while (startIdx >= 0) {
            if (nonNull(existingPE = get(path[startIdx].getHash()))) {
                break;
            }
            startIdx--;
        }
        existingPE = startIdx >= 0 ? existingPE : root;
        startIdx++;
        existingPE.addChildPath(Arrays.copyOfRange(path, startIdx, path.length));
    }

    public String dump(ContractDetails storage) {
        return root.toString(storage, 0);
    }

    public String dump() {
        return dump(null);
    }

    public static void dmp(PathElement el, Map<String, String> dump) {
        dump.put(toHexString(el.getHash()), toHexString(el.serialize()));
        el.getChildrenStream().forEach(child -> dmp(child, dump));
    }

    public Map<String, String> dmp() {
        Map<String, String> result = new HashMap<>();
        dmp(root, result);
        return result;
    }

    public static StorageDictionary readDmp(Map<String, String> dump) {
        HashMapDB<byte[]> storageDb = new HashMapDB<>();
        dump.entrySet().stream().forEach(entry -> storageDb.put(Hex.decode(entry.getKey()), Hex.decode(entry.getValue())));
        return new StorageDictionary(storageDb);
    }

    public PathElement getByPath(String... path) {
        PathElement result = root;
        for (String pathPart : path) {
            if (result == null) {
                  return null;
            }
            result = result.findChildByKey(pathPart);
        }

        return result;
    }

    public Set<DataWord> allKeys() {
        return findKeysIn(root, new HashSet<>());
    }

    // stack overflow may occur
    private Set<DataWord> findKeysIn(StorageDictionary.PathElement parent, Set<DataWord> keys) {
        parent.getChildren().forEach(child -> {
            if (child.hasChildren()) {
                findKeysIn(child, keys);
            } else {
                keys.add(new DataWord(child.storageKey));
            }
        });
        return keys;
    }
}
