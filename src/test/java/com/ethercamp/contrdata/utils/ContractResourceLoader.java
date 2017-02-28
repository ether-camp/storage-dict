package com.ethercamp.contrdata.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.vm.DataWord;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static java.util.stream.Collectors.joining;

public class ContractResourceLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @FunctionalInterface
    interface Loader<T> {
        T load(Resource resource) throws IOException;

    }

    private DefaultResourceLoader loader = new DefaultResourceLoader();

    private static String asLocation(String[] path) {
        return "classpath:contracts/" + Arrays.stream(path).collect(joining("/"));
    }

    private <T> T load(String location, Loader<T> loader) {
        try {
            Resource resource = this.loader.getResource(location);
            return loader.load(resource);
        } catch (IOException e) {
            throw new RuntimeException("Cannot load resource:", e);
        }
    }

    public byte[] loadAsBytes(String... path) {
        return load(asLocation(path), r -> Files.readAllBytes(Paths.get(r.getURI())));
    }

    public String loadAsString(String... path) {
        return new String(loadAsBytes(path));
    }

    public <T> T loadCompressedJsonAsObject(TypeReference<T> resultType, String... path) {
        return load(asLocation(path), r -> loadAndDecompress(r.getFile(), resultType));
    }

    public <T> T loadJsonAsObject(TypeReference<T> resultType, String... path) {
        return load(asLocation(path), r -> OBJECT_MAPPER.readValue(r.getFile(), resultType));
    }


    private static void compressAndSave(Path dir, String filleName, Object value) throws IOException {
        File file = new File(dir.toFile(), filleName + ".gz");
        try (OutputStream fos = new FileOutputStream(file)) {
            try (OutputStream gzos = new GZIPOutputStream(fos)) {
                OBJECT_MAPPER.writeValue(gzos, value);
            }
        }
    }

    private static <T> T loadAndDecompress(File file, TypeReference<T> typeRef) throws IOException {
        try (InputStream fis = new FileInputStream(file)) {
            try (InputStream gzis = new GZIPInputStream(fis)) {
                return OBJECT_MAPPER.readValue(gzis, typeRef);
            }
        }
    }

    public static void downloadStorage(String contractDir, String stateServiceUrl, String address) throws IOException {
        Path addrDir = Files.createDirectories(Paths.get(contractDir, address));

        Map<DataWord, DataWord> storage = StorageLoader.StoragePage.loadAll(stateServiceUrl, address).toMap();
        System.out.printf("Account 0x%s storage (%d entries) saving.\n", address, storage.size());
        compressAndSave(addrDir, "storage", storage);

        Map<String, String> dictionary = StorageLoader.loadStorageDictionaryDump(stateServiceUrl, address);
        System.out.printf("Account 0x%s storage dictionary (%d entries) saving.\n", address, dictionary.size());
        compressAndSave(addrDir, "dictionary", dictionary);
    }
}
