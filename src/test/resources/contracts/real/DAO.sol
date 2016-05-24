contract TokenInterface {
    mapping (address => uint256) balances;
    mapping (address => mapping (address => uint256)) allowed;

    /// @return Total amount of tokens
    uint256 public totalSupply;

    /// @param _owner The address from which the balance will be retrieved
    /// @return The balance
    function balanceOf(address _owner) constant returns (uint256 balance);

    /// @notice Send `_amount` tokens to `_to` from `msg.sender`
    /// @param _to The address of the recipient
    /// @param _amount The amount of tokens to be transferred
    /// @return Whether the transfer was successful or not
    function transfer(address _to, uint256 _amount) returns (bool success);

    /// @notice Send `_amount` tokens to `_to` from `_from` on the condition it
    /// is approved by `_from`
    /// @param _from The address of the sender
    /// @param _to The address of the recipient
    /// @param _amount The amount of tokens to be transferred
    /// @return Whether the transfer was successful or not
    function transferFrom(address _from, address _to, uint256 _amount) returns (bool success);

    /// @notice `msg.sender` approves `_spender` to spend `_amount` tokens on
    /// its behalf
    /// @param _spender The address of the account able to transfer the tokens
    /// @param _amount The amount of tokens to be approved for transfer
    /// @return Whether the approval was successful or not
    function approve(address _spender, uint256 _amount) returns (bool success);

    /// @param _owner The address of the account owning tokens
    /// @param _spender The address of the account able to transfer the tokens
    /// @return Amount of remaining tokens of _owner that _spender is allowed
    /// to spend
    function allowance(
        address _owner,
        address _spender
    ) constant returns (uint256 remaining);

    event Transfer(address indexed _from, address indexed _to, uint256 _amount);
    event Approval(
        address indexed _owner,
        address indexed _spender,
        uint256 _amount
    );
}


contract Token is TokenInterface {
    // Protects users by preventing the execution of method calls that
    // inadvertently also transferred ether
    modifier noEther() {if (msg.value > 0) throw; _}

    function balanceOf(address _owner) constant returns (uint256 balance) {
        return balances[_owner];
    }

    function transfer(address _to, uint256 _amount) noEther returns (bool success) {
        if (balances[msg.sender] >= _amount && _amount > 0) {
            balances[msg.sender] -= _amount;
            balances[_to] += _amount;
            Transfer(msg.sender, _to, _amount);
            return true;
        } else {
           return false;
        }
    }

    function transferFrom(
        address _from,
        address _to,
        uint256 _amount
    ) noEther returns (bool success) {

        if (balances[_from] >= _amount
            && allowed[_from][msg.sender] >= _amount
            && _amount > 0) {

            balances[_to] += _amount;
            balances[_from] -= _amount;
            allowed[_from][msg.sender] -= _amount;
            Transfer(_from, _to, _amount);
            return true;
        } else {
            return false;
        }
    }

    function approve(address _spender, uint256 _amount) returns (bool success) {
        allowed[msg.sender][_spender] = _amount;
        Approval(msg.sender, _spender, _amount);
        return true;
    }

    function allowance(address _owner, address _spender) constant returns (uint256 remaining) {
        return allowed[_owner][_spender];
    }
}

contract TokenSaleInterface {

    // End of token sale, in Unix time
    uint public closingTime;
    // Minimum funding goal of the token sale, denominated in tokens
    uint public minValue;
    // True if the DAO reached its minimum funding goal, false otherwise
    bool public isFunded;
    // For DAO splits - if privateSale is 0, then it is a public sale, otherwise
    // only the address stored in privateSale is allowed to purchase tokens
    address public privateSale;
    // hold extra ether which has been paid after the DAO token price has increased
    ManagedAccount public extraBalance;
    // tracks the amount of wei given from each contributor (used for refund)
    mapping (address => uint256) weiGiven;

    /// @dev Constructor setting the minimum funding goal and the
    /// end of the Token Sale
    /// @param _minValue Token Sale minimum funding goal
    /// @param _closingTime Date (in Unix time) of the end of the Token Sale
    // This is the constructor: it can not be overloaded so it is commented out
    //  function TokenSale(uint _minValue, uint _closingTime);

    /// @notice Buy Token with `_tokenHolder` as the initial owner of the Token
    /// @param _tokenHolder The address of the Tokens's recipient
    function buyTokenProxy(address _tokenHolder) returns (bool success);

    /// @notice Refund `msg.sender` in the case the Token Sale didn't reach its
    /// minimum funding goal
    function refund();

    /// @return the divisor used to calculate the token price during the sale
    function divisor() returns (uint divisor);

    event FundingToDate(uint value);
    event SoldToken(address indexed to, uint amount);
    event Refund(address indexed to, uint value);
}


contract TokenSale is TokenSaleInterface, Token {
    function TokenSale(uint _minValue, uint _closingTime, address _privateSale) {
        closingTime = _closingTime;
        minValue = _minValue;
        privateSale = _privateSale;
        extraBalance = new ManagedAccount(address(this));
    }

    function buyTokenProxy(address _tokenHolder) returns (bool success) {
        if (now < closingTime && msg.value > 0
            && (privateSale == 0 || privateSale == msg.sender)) {

            uint token = (msg.value * 20) / divisor();
            extraBalance.call.value(msg.value - token)();
            balances[_tokenHolder] += token;
            totalSupply += token;
            weiGiven[msg.sender] += msg.value;
            SoldToken(_tokenHolder, token);
            if (totalSupply >= minValue && !isFunded) {
                isFunded = true;
                FundingToDate(totalSupply);
            }
            return true;
        }
        throw;
    }

    function refund() noEther {
        if (now > closingTime && !isFunded) {
            // get extraBalance - will only succeed when called for the first time
            extraBalance.payOut(address(this), extraBalance.accumulatedInput());

            // execute refund
            if (msg.sender.call.value(weiGiven[msg.sender])()) {
                Refund(msg.sender, weiGiven[msg.sender]);
                totalSupply -= balances[msg.sender];
                balances[msg.sender] = 0;
                weiGiven[msg.sender] = 0;
            }
        }
    }

    function divisor() returns (uint divisor) {
        // the number of (base unit) tokens per wei is calculated
        // as `msg.value` * 20 / `divisor`
        // the funding period starts with a 1:1 ratio
        if (closingTime - 5 days > now) {
            return 20;
        // followed by 10 days with a daily price increase of 5%
        } else if (closingTime - 1 days > now) {
            return (20 + (now - (closingTime - 5 days)) / (1 days));
        // the last 4 days there is a constant price ratio of 1:1,5
        } else {
            return 30;
        }
    }
}

contract DAOInterface {

    // Proposals to spend the DAO's ether or to choose a new service provider
    Proposal[] public proposals;
    // The quorum needed for each proposal is partially calculated by
    // totalSupply / minQuorumDivisor
    uint minQuorumDivisor;
    // The unix time of the last time quorum was reached on a proposal
    uint lastTimeMinQuorumMet;
    // The total amount of wei received as reward that has not been sent to
    // the rewardAccount
    uint public rewards;
    // Address of the service provider
    address public serviceProvider;
    // The whitelist: List of addresses the DAO is allowed to send money to
    address[] public allowedRecipients;

    // Tracks the addresses that own Reward Tokens. Those addresses can only be
    // DAOs that have split from the original DAO. Conceptually, Reward Tokens
    // represent the proportion of the rewards that the DAO has the right to
    // receive. These Reward Tokens are generated when the DAO spends ether.
    mapping (address => uint) public rewardToken;
    // Total supply of rewardToken
    uint public totalRewardToken;

    // The account used to manage the rewards which are to be distributed to the
    // DAO Token Holders of any DAO that holds Reward Tokens
    ManagedAccount public rewardAccount;
    // Amount of rewards (in wei) already paid out to a certain address
    mapping (address => uint) public paidOut;
    // Map of addresses blocked during a vote (not allowed to transfer DAO
    // tokens). The address points to the proposal ID.
    mapping (address => uint) public blocked;

    // The minimum deposit (in wei) required to submit any proposal that is not
    // requesting a new service provider (no deposit is required for splits)
    uint public proposalDeposit;

    // Contract that is able to create a new DAO (with the same code as
    // this one), used for splits
    DAO_Creator public daoCreator;

    // A proposal with `newServiceProvider == false` represents a transaction
    // to be issued by this DAO
    // A proposal with `newServiceProvider == true` represents a DAO split
    struct Proposal {
        // The address where the `amount` will go to if the proposal is accepted
        // or if `newServiceProvider` is true, the proposed service provider of
        //the new DAO).
        address recipient;
        // The amount to transfer to `recipient` if the proposal is accepted.
        uint amount;
        // A plain text description of the proposal
        string description;
        // A unix timestamp, denoting the end of the voting period
        uint votingDeadline;
        // True if the proposal's votes have yet to be counted, otherwise False
        bool open;
        // True if quorum has been reached, the votes have been counted, and
        // the majority said yes
        bool proposalPassed;
        // A hash to check validity of a proposal
        bytes32 proposalHash;
        // Deposit in wei the creator added when submitting their proposal. It
        // is taken from the msg.value of a newProposal call.
        uint proposalDeposit;
        // True if this proposal is to assign a new service provider
        bool newServiceProvider;
        // Data needed for splitting the DAO
        SplitData[] splitData;
        // Number of tokens in favour of the proposal
	address newDAO;
        uint yea;
        // Number of tokens opposed to the proposal
        uint nay;
        // Simple mapping to check if a shareholder has voted for it
        mapping (address => bool) votedYes;
        // Simple mapping to check if a shareholder has voted against it
        mapping (address => bool) votedNo;
        // Address of the shareholder who created the proposal
        address creator;
    }

    // Used only in the case of a newServiceProvider porposal.
    struct SplitData {
        // The balance of the current DAO minus the deposit at the time of split
        uint splitBalance;
        // The total amount of DAO Tokens in existence at the time of split.
        uint totalSupply;
        // Amount of Reward Tokens owned by the DAO at the time of split.
        uint rewardToken;
        // The new DAO contract created at the time of split.
        DAO newDAO;
    }
    // Used to restrict acces to certain functions to only DAO Token Holders
    modifier onlyTokenholders {}

    /// @dev Constructor setting the default service provider and the address
    /// for the contract able to create another DAO as well as the parameters
    /// for the DAO Token Sale
    /// @param _defaultServiceProvider The default service provider
    /// @param _daoCreator The contract able to (re)create this DAO
    /// @param _minValue Minimal value for a successful DAO Token Sale
    /// @param _closingTime Date (in unix time) of the end of the DAO Token Sale
    /// @param _privateSale If zero the DAO Token Sale is open to public, a
    /// non-zero address means that the DAO Token Sale is only for the address
    // This is the constructor: it can not be overloaded so it is commented out
    //  function DAO(
        //  address _defaultServiceProvider,
        //  DAO_Creator _daoCreator,
        //  uint _minValue,
        //  uint _closingTime,
        //  address _privateSale
    //  )

    /// @notice Buy Token with `msg.sender` as the beneficiary
    function () returns (bool success);

    /// @dev Function used by the products of the DAO (e.g. Slocks) to send
    /// rewards to the DAO
    /// @return Whether the call to this function was successful or not
    function payDAO() returns(bool);

    /// @dev This function is used by the service provider to send money back
    /// to the DAO, it can also be used to receive payments that should not be
    /// counted as rewards (donations, grants, etc.)
    /// @return Whether the DAO received the ether successfully
    function receiveEther() returns(bool);

    /// @notice `msg.sender` creates a proposal to send `_amount` Wei to
    ///  `_recipient` with the transaction data `_transactionData`. If
    /// `_newServiceProvider` is true, then this is a proposal that splits the
    /// DAO and sets `_recipient` as the new DAO's new service provider.
    /// @param _recipient Address of the recipient of the proposed transaction
    /// @param _amount Amount of wei to be sent with the proposed transaction
    /// @param _description String describing the proposal
    /// @param _transactionData Data of the proposed transaction
    /// @param _debatingPeriod Time used for debating a proposal, at least 2
    /// weeks for a regular proposal, 10 days for new service provider proposal
    /// @param _newServiceProvider Bool defining whether this proposal is about
    /// a new service provider or not
    /// @return The proposal ID. Needed for voting on the proposal
    function newProposal(
        address _recipient,
        uint _amount,
        string _description,
        bytes _transactionData,
        uint _debatingPeriod,
        bool _newServiceProvider
    ) onlyTokenholders returns (uint _proposalID);

    /// @notice Check that the proposal with the ID `_proposalID` matches the
    /// transaction which sends `_amount` with data `_transactionData`
    /// to `_recipient`
    /// @param _proposalID The proposal ID
    /// @param _recipient The recipient of the proposed transaction
    /// @param _amount The amount of wei to be sent in the proposed transaction
    /// @param _transactionData The data of the proposed transaction
    /// @return Whether the proposal ID matches the transaction data or not
    function checkProposalCode(
        uint _proposalID,
        address _recipient,
        uint _amount,
        bytes _transactionData
    ) constant returns (bool _codeChecksOut);

    /// @notice Vote on proposal `_proposalID` with `_supportsProposal`
    /// @param _proposalID The proposal ID
    /// @param _supportsProposal Yes/No - support of the proposal
    /// @return The vote ID.
    function vote(
        uint _proposalID,
        bool _supportsProposal
    ) onlyTokenholders returns (uint _voteID);

    /// @notice Checks whether proposal `_proposalID` with transaction data
    /// `_transactionData` has been voted for or rejected, and executes the
    /// transaction in the case it has been voted for.
    /// @param _proposalID The proposal ID
    /// @param _transactionData The data of the proposed transaction
    /// @return Whether the proposed transaction has been executed or not
    function executeProposal(
        uint _proposalID,
        bytes _transactionData
    ) returns (bool _success);

    /// @notice ATTENTION! I confirm to move my remaining funds to a new DAO
    /// with `_newServiceProvider` as the new service provider, as has been
    /// proposed in proposal `_proposalID`. This will burn my tokens. This can
    /// not be undone and will split the DAO into two DAO's, with two
    /// different underlying tokens.
    /// @param _proposalID The proposal ID
    /// @param _newServiceProvider The new service provider of the new DAO
    /// @dev This function, when called for the first time for this proposal,
    /// will create a new DAO and send the sender's portion of the remaining
    /// ether and Reward Tokens to the new DAO. It will also burn the DAO Tokens
    /// of the sender. (TODO: document rewardTokens - done??)
    function splitDAO(
        uint _proposalID,
        address _newServiceProvider
    ) returns (bool _success);

    /// @notice Add a new possible recipient `_recipient` to the whitelist so
    /// that the DAO can send transactions to them (using proposals)
    /// @param _recipient New recipient address
    /// @dev Can only be called by the current service provider
    function addAllowedAddress(address _recipient) external returns (bool _success);

    /// @notice Change the minimum deposit required to submit a proposal
    /// @param _proposalDeposit The new proposal deposit
    /// @dev Can only be called by this DAO (through proposals with the
    /// recipient being this DAO itself)
    function changeProposalDeposit(uint _proposalDeposit) external;

    /// @notice Get my portion of the reward that was sent to `rewardAccount`
    /// @return Whether the call was successful
    function getMyReward() returns(bool _success);

    /// @notice Withdraw `account`'s portion of the reward from `rewardAccount`,
    /// to `account`'s balance
    /// @return Whether the call was successful
    function withdrawRewardFor(address _account) returns(bool _success);

    /// @notice Send `_amount` tokens to `_to` from `msg.sender`. Prior to this
    /// getMyReward() is called.
    /// @param _to The address of the recipient
    /// @param _amount The amount of tokens to be transfered
    /// @return Whether the transfer was successful or not
    function transferWithoutReward(address _to, uint256 _amount) returns (bool success);

    /// @notice Send `_amount` tokens to `_to` from `_from` on the condition it
    /// is approved by `_from`. Prior to this getMyReward() is called.
    /// @param _from The address of the sender
    /// @param _to The address of the recipient
    /// @param _amount The amount of tokens to be transfered
    /// @return Whether the transfer was successful or not
    function transferFromWithoutReward(
        address _from,
        address _to,
        uint256 _amount
    ) returns (bool success);

    /// @notice Doubles the 'minQuorumDivisor' in the case quorum has not been
    /// achieved in 52 weeks
    /// @return Whether the change was successful or not
    function halveMinQuorum() returns (bool _success);

    /// @return total number of proposals ever created
    function numberOfProposals() constant returns (uint _numberOfProposals);

    /// @param _account The address of the account which is checked.
    /// @return Whether the account is blocked (not allowed to transfer tokens) or not.
    function isBlocked(address _account) returns (bool);


    event ProposalAdded(
        uint indexed proposalID,
        address recipient,
        uint amount,
        bool newServiceProvider,
        string description
    );
    event Voted(uint indexed proposalID, bool position, address indexed voter);
    event ProposalTallied(uint indexed proposalID, bool result, uint quorum);
    event NewServiceProvider(address indexed _newServiceProvider);
    event AllowedRecipientAdded(address indexed _recipient);
}

// The DAO contract itself
contract DAO is DAOInterface, Token, TokenSale {

    // Modifier that allows only shareholders to vote and create new proposals
    modifier onlyTokenholders {
        if (balanceOf(msg.sender) == 0) throw;
            _
    }

    function DAO(
        address _defaultServiceProvider,
        DAO_Creator _daoCreator,
        uint _minValue,
        uint _closingTime,
        address _privateSale
    ) TokenSale(_minValue, _closingTime, _privateSale) {

        serviceProvider = _defaultServiceProvider;
        daoCreator = _daoCreator;
        proposalDeposit = 1 ether;
        rewardAccount = new ManagedAccount(address(this));
        lastTimeMinQuorumMet = now;
        minQuorumDivisor = 5; // sets the minimal quorum to 20%
        proposals.length++; // avoids a proposal with ID 0 because it is used
        if (address(rewardAccount) == 0)
            throw;
    }

    function () returns (bool success) {
        if (now < closingTime + 40 days)
            return buyTokenProxy(msg.sender);
        else
            return receiveEther();
    }


    function payDAO() returns (bool) {
        rewards += msg.value;
        return true;
    }

    function receiveEther() returns (bool) {
        return true;
    }


    function newProposal(
        address _recipient,
        uint _amount,
        string _description,
        bytes _transactionData,
        uint _debatingPeriod,
        bool _newServiceProvider
    ) onlyTokenholders returns (uint _proposalID) {

        // Sanity check
        if (_newServiceProvider && (
            _amount != 0
            || _transactionData.length != 0
            || _recipient == serviceProvider
            || msg.value > 0
            || _debatingPeriod < 1 minutes)) {
            throw;
        } else if(
            !_newServiceProvider
            && (!isRecipientAllowed(_recipient) || (_debatingPeriod < 1 minutes))
        ) {
            throw;
        }

        if (!isFunded
            || now < closingTime
            || (msg.value < proposalDeposit && !_newServiceProvider)) {

            throw;
        }

        if (_recipient == address(rewardAccount) && _amount > rewards)
            throw;

        if (now + _debatingPeriod < now) // prevents overflow
            throw;

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
        if (_newServiceProvider)
            p.splitData.length++;
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


    function checkProposalCode(
        uint _proposalID,
        address _recipient,
        uint _amount,
        bytes _transactionData
    ) noEther constant returns (bool _codeChecksOut) {
        Proposal p = proposals[_proposalID];
        return p.proposalHash == sha3(_recipient, _amount, _transactionData);
    }


    function vote(
        uint _proposalID,
        bool _supportsProposal
    ) onlyTokenholders noEther returns (uint _voteID) {

        Proposal p = proposals[_proposalID];
        if (p.votedYes[msg.sender]
            || p.votedNo[msg.sender]
            || now >= p.votingDeadline) {

            throw;
        }

        if (_supportsProposal) {
            p.yea += balances[msg.sender];
            p.votedYes[msg.sender] = true;
        } else {
            p.nay += balances[msg.sender];
            p.votedNo[msg.sender] = true;
        }

        if (blocked[msg.sender] == 0) {
            blocked[msg.sender] = _proposalID;
        } else if (p.votingDeadline > proposals[blocked[msg.sender]].votingDeadline) {
            // this proposal's voting deadline is further into the future than
            // the proposal that blocks the sender so make it the blocker
            blocked[msg.sender] = _proposalID;
        }

        Voted(_proposalID, _supportsProposal, msg.sender);
    }


    function executeProposal(
        uint _proposalID,
        bytes _transactionData
    ) noEther returns (bool _success) {

        Proposal p = proposals[_proposalID];
        // Check if the proposal can be executed
        if (now < p.votingDeadline  // has the voting deadline arrived?
            // Have the votes been counted?
            || !p.open
            // Does the transaction code match the proposal?
            || p.proposalHash != sha3(p.recipient, p.amount, _transactionData)) {

            throw;
        }

        if (p.newServiceProvider) {
            p.open = false;
            return;
        }

        uint quorum = p.yea + p.nay;

        // Execute result
        if (quorum >= minQuorum(p.amount) && p.yea > p.nay) {
            if (!p.creator.send(p.proposalDeposit))
                throw;
            // Without this throw, the creator of the proposal can repeat this,
            // and get so much ether
            if (!p.recipient.call.value(p.amount)(_transactionData))
                throw;
            p.proposalPassed = true;
            _success = true;
            lastTimeMinQuorumMet = now;
            if (p.recipient == address(rewardAccount)) {
                // This happens when multiple similar proposals are created and
                // both are passed at the same time.
                if (rewards < p.amount)
                    throw;
                rewards -= p.amount;
            } else {
                rewardToken[address(this)] += p.amount;
                totalRewardToken += p.amount;
            }
        } else if (quorum >= minQuorum(p.amount) && p.nay >= p.yea) {
            if (!p.creator.send(p.proposalDeposit))
                throw;
            lastTimeMinQuorumMet = now;
        }

        // Since the voting deadline is over, close the proposal
        p.open = false;

        // Initiate event
        ProposalTallied(_proposalID, _success, quorum);
    }


    function splitDAO(
        uint _proposalID,
        address _newServiceProvider
    ) noEther onlyTokenholders returns (bool _success) {

        Proposal p = proposals[_proposalID];

        // Sanity check

        if (now < p.votingDeadline  // has the voting deadline arrived?
            //The request for a split expires 41 days after the voting deadline
            || now > p.votingDeadline + 41 days
            // Does the new service provider address match?
            || p.recipient != _newServiceProvider
            // Is it a new service provider proposal?
            || !p.newServiceProvider
            // Have you voted for this split?
            || !p.votedYes[msg.sender]
            // Did you already vote on another proposal?
            || blocked[msg.sender] != _proposalID) {

            throw;
        }

        // If the new DAO doesn't exist yet, create the new DAO and store the
        // current split data
        if (address(p.splitData[0].newDAO) == 0) {
            p.splitData[0].newDAO = createNewDAO(_newServiceProvider);
	p.newDAO = p.splitData[0].newDAO;
            // Call depth limit reached, etc.
            if (address(p.splitData[0].newDAO) == 0)
                throw;
            // p.proposalDeposit should be zero here
            if (this.balance < p.proposalDeposit)
                throw;
            p.splitData[0].splitBalance = this.balance - p.proposalDeposit;
            p.splitData[0].rewardToken = rewardToken[address(this)];
            p.splitData[0].totalSupply = totalSupply;
            p.proposalPassed = true;
        }

        // Move funds and assign new Tokens
        uint fundsToBeMoved =
            (balances[msg.sender] * p.splitData[0].splitBalance) /
            p.splitData[0].totalSupply;
        if (p.splitData[0].newDAO.buyTokenProxy.value(fundsToBeMoved)(msg.sender) == false)
            throw;


        // Assign reward rights to new DAO
        uint rewardTokenToBeMoved =
            (balances[msg.sender] * p.splitData[0].rewardToken) /
            p.splitData[0].totalSupply;
        rewardToken[address(p.splitData[0].newDAO)] += rewardTokenToBeMoved;
        if (rewardToken[address(this)] < rewardTokenToBeMoved)
            throw;
        rewardToken[address(this)] -= rewardTokenToBeMoved;

        // Burn DAO Tokens
        Transfer(msg.sender, 0, balances[msg.sender]);
        totalSupply -= balances[msg.sender];
        balances[msg.sender] = 0;
        paidOut[address(p.splitData[0].newDAO)] += paidOut[msg.sender];
        paidOut[msg.sender] = 0;

        return true;
    }


    function getMyReward() noEther returns (bool _success) {
        return withdrawRewardFor(msg.sender);
    }


    function withdrawRewardFor(address _account) noEther returns (bool _success) {
        // The account's portion of Reward Tokens of this DAO
        uint portionOfTheReward =
            (balanceOf(_account) * rewardToken[address(this)]) /
            totalSupply + rewardToken[_account];
        uint reward =
            (portionOfTheReward * rewardAccount.accumulatedInput()) /
            totalRewardToken - paidOut[_account];
        if (!rewardAccount.payOut(_account, reward))
            throw;
        paidOut[_account] += reward;
        return true;
    }


    function transfer(address _to, uint256 _value) returns (bool success) {
        if (isFunded
            && now > closingTime
            && !isBlocked(msg.sender)
            && transferPaidOut(msg.sender, _to, _value)
            && super.transfer(_to, _value)) {

            return true;
        } else {
            throw;
        }
    }


    function transferWithoutReward(address _to, uint256 _value) returns (bool success) {
        if (!getMyReward())
            throw;
        return transfer(_to, _value);
    }


    function transferFrom(address _from, address _to, uint256 _value) returns (bool success) {
        if (isFunded
            && now > closingTime
            && !isBlocked(_from)
            && transferPaidOut(_from, _to, _value)
            && super.transferFrom(_from, _to, _value)) {

            return true;
        } else {
            throw;
        }
    }


    function transferFromWithoutReward(
        address _from,
        address _to,
        uint256 _value
    ) returns (bool success) {

        if (!withdrawRewardFor(_from))
            throw;
        return transferFrom(_from, _to, _value);
    }


    function transferPaidOut(
        address _from,
        address _to,
        uint256 _value
    ) internal returns (bool success) {

        uint transferPaidOut = paidOut[_from] * _value / balanceOf(_from);
        if (transferPaidOut > paidOut[_from])
            throw;
        paidOut[_from] -= transferPaidOut;
        paidOut[_to] += transferPaidOut;
        return true;
    }


    function changeProposalDeposit(uint _proposalDeposit) noEther external {
        if (msg.sender != address(this) || _proposalDeposit > this.balance / 10)
            throw;
        proposalDeposit = _proposalDeposit;
    }


    function addAllowedAddress(address _recipient) noEther external returns (bool _success) {
        if (msg.sender != serviceProvider)
            throw;
        allowedRecipients.push(_recipient);
        return true;
    }


    function isRecipientAllowed(address _recipient) internal returns (bool _isAllowed) {
        if (_recipient == serviceProvider
            || _recipient == address(rewardAccount)
            || _recipient == address(this)
            || (_recipient == address(extraBalance)
                // only allowed when at least the amount held in the
                // extraBalance account has been spent from the DAO
                && totalRewardToken > extraBalance.accumulatedInput()))
            return true;

        for (uint i = 0; i < allowedRecipients.length; ++i) {
            if (_recipient == allowedRecipients[i])
                return true;
        }
        return false;
    }


    function minQuorum(uint _value) internal returns (uint _minQuorum) {
        // minimum of 20% and maximum of 53.33%
        return totalSupply / minQuorumDivisor + _value / 3;
    }


    function halveMinQuorum() returns (bool _success) {
        if (lastTimeMinQuorumMet < (now - 52 weeks)) {
            lastTimeMinQuorumMet = now;
            minQuorumDivisor *= 2;
            return true;
        } else {
            return false;
        }
    }


    function createNewDAO(address _newServiceProvider) internal returns (DAO _newDAO) {
        NewServiceProvider(_newServiceProvider);
        return daoCreator.createDAO(_newServiceProvider, 0, now + 5 minutes);
    }


    function numberOfProposals() constant returns (uint _numberOfProposals) {
        // Don't count index 0. It's used by isBlocked() and exists from start
        return proposals.length - 1;
    }


    function isBlocked(address _account) returns (bool) {
        if (blocked[_account] == 0)
            return false;
        Proposal p = proposals[blocked[_account]];
        if (!p.open) {
            blocked[_account] = 0;
            return false;
        } else {
            return true;
        }
    }
}

contract DAO_Creator {
    function createDAO(
        address _defaultServiceProvider,
        uint _minValue,
        uint _closingTime
    ) returns (DAO _newDAO) {

        return new DAO(
            _defaultServiceProvider,
            DAO_Creator(this),
            _minValue,
            _closingTime,
            msg.sender
        );
    }
}


contract ManagedAccountInterface {
    address public owner;
    uint public accumulatedInput;

    function payOut(address _recipient, uint _amount) returns (bool);

    event PayOut(address indexed _recipient, uint _amount);
}


contract ManagedAccount is ManagedAccountInterface{
    function ManagedAccount(address _owner) {
        owner = _owner;
    }

    function() {
        accumulatedInput += msg.value;
    }

    function payOut(address _recipient, uint _amount) returns (bool) {
        if (msg.sender != owner || msg.value > 0)
            throw;
        if (_recipient.call.value(_amount)()) {
            PayOut(_recipient, _amount);
            return true;
        } else {
            return false;
        }
    }
}