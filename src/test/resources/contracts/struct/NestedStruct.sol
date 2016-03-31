contract NestedStruct {

        struct Child {
                string name;
        }

        struct Parent {
                string name;
                Child child;
        }

        Parent current;
        Parent[] archive;

        function NestedStruct() {
                current = Parent('georgy', Child('dmitry'));
        }

        function newParent(string parentName, string childName) {
                archive.push(current);
                current = Parent(parentName, Child(childName));
        }

        function changeChild(string name) {
                current.child.name = name;
        }
}