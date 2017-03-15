contract PackingTest {

    enum Enum {
        ONE, TWO, THREE
    }

    struct EnumAfterAddress {
        address fAddress;
        Enum fEnum;
        bool fBool;
        uint fInt;
    }

    EnumAfterAddress mStruct;
    bool mBool = true;
    address mAddress = 0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa;

    function PackingTest() {
        mStruct.fAddress = 0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb;
        mStruct.fEnum = Enum.THREE;
        mStruct.fBool = true;
        mStruct.fInt = 256;
    }

    function toggle() returns (bool enabled) {
        return mBool = !mBool;
    }
}