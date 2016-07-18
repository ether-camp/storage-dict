contract RemovedFields {

    address[] public addresses;

    function RemovedFields() {
        addresses.push(0xaeef46db4855e25702f8237e8f403fddcafcccc);
        addresses.push(0xaeef46db4855e25702f8237e8f403fddcafcaaa);
        addresses.push(0xaeef46db4855e25702f8237e8f403fddcafcacc);

        addresses[1] = 0;
    }
}