contract TestBoolAfterPackedStruct {

    struct Struct {
        bool fBool;
        address fAddr;
    }

    Struct mStruct;
    bool mBool = true;

    function TestBoolAfterPackedStruct() {
        mStruct.fAddr = 0x1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c;
    }
}