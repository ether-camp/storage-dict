contract TestNestedStruct {
    
    struct Person {
        address addr;
        string name;
    }
    
    struct Marriage {
        Person wife;
        Person husband;
    }
    
    Marriage single;
    uint sep1 = 256;
    Marriage[] dynArray;
    uint sep2 = 256;
    Marriage[3] staticArray;
    uint sep3 = 256;
    
    function TestNestedStruct() {
        single.wife.addr = 0xabababababababababababababababababababab;
        single.wife.name = 'Ann';
        single.husband.addr = 0xcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcf;
        single.husband.name = 'Eugene';

        staticArray[1].wife.addr = 0xffffabababababababababababababababababab;
        staticArray[1].wife.name = 'Ann-1';
        staticArray[1].husband.addr = 0xffffcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcf;
        staticArray[1].husband.name = 'Eugene-1';
    }
}