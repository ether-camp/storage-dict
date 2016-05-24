contract ShiftedArray {

    event ProposalAdded(
        uint indexed proposalID,
        address recipient,
        uint amount,
        bool newServiceProvider,
        string description
    );

    struct Proposal {
        address recipient;
        uint amount;
        string description;
        uint votingDeadline;
        bool open;
        bool proposalPassed;
        bytes32 proposalHash;
        uint proposalDeposit;
        bool newServiceProvider;
    	address newDAO;
        uint yea;
        uint nay;
        mapping (address => bool) votedYes;
        mapping (address => bool) votedNo;
        address creator;
    }

    Proposal[] public proposals;


    function ShiftedArray() {
        proposals.length++; // avoids a proposal with ID 0 because it is used

        newProposal(
            0xaeef46db4855e25702f8237e8f403fddcafcccc,
            123231,
            'prop desc',
            new bytes(0x123aaa),
            1000000,
            true
        );

        newProposal(
            0xaeef46db4855e25702f8237e8f403fddcafbbbb,
            222333222,
            'prop desc 1',
            new bytes(0x123ccc),
            1000001,
            true
        );

        newProposal(
            0xaeef46db4855e25702f8237e8f403fddcafbbbb,
            222333222,
            'prop desc 2',
            new bytes(0x123ccc),
            1000001,
            true
        );

    }


    function newProposal(
        address _recipient,
        uint _amount,
        string _description,
        bytes _transactionData,
        uint _debatingPeriod,
        bool _newServiceProvider
    ) returns (uint _proposalID) {

        _proposalID = proposals.length++;
        Proposal p = proposals[_proposalID];
        p.recipient = _recipient;
        p.amount = _amount;
        p.description = _description;
        p.proposalHash = sha3(_recipient, _amount, _transactionData);
        p.votingDeadline = now + _debatingPeriod;
        p.open = true;
        //p.proposalPassed = False; // that's default
        p.newServiceProvider = _newServiceProvider;
        p.creator = msg.sender;
        p.proposalDeposit = msg.value;
        ProposalAdded(
            _proposalID,
            _recipient,
            _amount,
            _newServiceProvider,
            _description
        );
    }
}