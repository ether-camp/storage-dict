contract TestStruct {

    struct Person {
        string firstName;
        string secondName;
        uint age;
        address account;
    }

    Person wife;
    Person husband;

    function TestStruct() {
        wife = Person('Angelina', 'Jolie', 40, 0xabcdefabcdefabcdefabcdefabcdefabcdefabcd);
        husband = Person('Brad', 'Pitt', 53, 0x1234567890123456789012345678901234567890);
    }
}