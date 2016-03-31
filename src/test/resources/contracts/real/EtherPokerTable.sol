//
// This is the central structure for a poker table.
// In order to join a game a player needs to have the address
// of this contract as well as a passcode (TODO: hash the thing)
// The player then sends a join_table() transaction and, when
// it is published, can then query the table to find the
// Peer-2-peer transport addresses of some other players, as well
// as other information about the table.
//
// Note that in general communication between players
// (including gameplay) is NOT handled through the contract, but
// instead through a p2p "subnet" (maybe whisper, maybe telehash)
//
// Also: be careful about methods that return "bytes32" as strings:
// unless you specifically unpack them python will treat them as
// 32-entry byte arrays (as it should)
//

contract EtherPokerTable {

        struct Player {
                int16 seat; // integer, starting at 0, clockwise
                address addr; // player account address
                string p2pID; // id/address, probably whisper ID, used to speak via p2p
                bytes32 nickName;
        }

        // publics
        bytes32 public tableName = "init";
        uint128 public buyIn = 5000 finney;
        uint128 public minBet = 200 finney;
        int8 public maxPlayers = 1; // includes owner
        int8 public gameType = 0; // see table definition
        int8 public tableClosed = 0; // Closed to joiners?

        // private'ish
        address _owner;
        bytes32 _tablePasscode; // TODO: use hash in production
        Player[] _playersBySeat; // index is the player's seat

        // Be careful here. Since Maps return 0 for non-existent entries
        // we need to store "seat+1". Alternatively, we could decide that
        // seats are always 1-indexed, but that'd absolutely result in bugs elsewhere
        // in the app when folks assumed they were 0-indexed. So we'll
        // just contain it here.
        mapping(address => int16) _playerSeatsPlus1;

        // Gas measured: ?? TODO
        function EtherPokerTable(bytes32 name, bytes32 passcode, uint128 buy_in, uint128 min_bet,
                int8 max_players, int8 game_type) {
                _owner = msg.sender;
                tableName = name;
                _tablePasscode = passcode;
                buyIn = buy_in;
                minBet = min_bet;
                maxPlayers = max_players;
                gameType = game_type;
                tableClosed = 0;
        }

        //
        // Close table to new joiners before
        // starting gameplay
        //
        function close_table() {
                if (msg.sender == _owner) {
                        tableClosed = 1;
                }
        }

        //
        // Idea here is that you can call() this method for pre-verification to test
        // for trivial issues. Then you issue a transaction and check the receipt
        // for non-zero contract address on success or a log entry containing an error msg
        //
        // Gas measured: 240526
        function join_table(address addr, string p2pID, bytes32 nickName, bytes32 passcode) returns(bytes32) {
                bytes32 errMsg = '';
                if (passcode == _tablePasscode) {
                        if (_playerSeatsPlus1[msg.sender] == 0) {
                                if (int8(_playersBySeat.length) >= maxPlayers) {
                                        errMsg = 'Game Full';
                                } else if (tableClosed == 0) {
                                        // new player
                                        int16 idx = int16(_playersBySeat.length);
                                        _playersBySeat.length++;
                                        _playersBySeat[uint(idx)] = Player({
                                                seat: idx,
                                                addr: addr,
                                                p2pID: p2pID,
                                                nickName: nickName
                                        });
                                        _playerSeatsPlus1[msg.sender] = idx + 1;

                                        if (_playerSeatsPlus1[msg.sender] != (idx + 1))
                                                errMsg = 'Seat assignment failed';

                                } else
                                        errMsg = 'Game Closed';
                        } else {
                                // re-connection (can do even if closed - is that OK? Probably not)
                                int16 seat = _playerSeatsPlus1[msg.sender] - 1;
                                Player p = _playersBySeat[uint(seat)];
                                p.p2pID = p2pID;
                                // Do NOT update nickname
                        }
                } else
                        errMsg = 'Bad Passcode';

                log1(errMsg, 'errMsg');
                return errMsg;
        }

        // This is how you find out your own seat.
        function get_player_seat() returns(int16) {
                return _playerSeatsPlus1[msg.sender] - 1;
        }

        function get_minus_one() returns(int16) {
                int16 val = -1;
                return val;
        }


        //
        function owners_seat() returns(int16) {
                // return int16(_playerSeatsPlus1[_owner]) - 1;

                // Should be the above, but it looks like a -1
                // returned in an int16 isn;t working. A simple
                // "return -1;" doesnt work either.

                return _playerSeatsPlus1[_owner] - 1;
        }

        // To connect to other players you first ask for how many there are,
        // and then for each you ask for a p2pAddress.
        // Once you have at least one you can pass it (or more) to the p2p
        // networking layer as a seed(s)
        function get_player_count() returns(int16) {
                int16 count = -1;
                if (_playerSeatsPlus1[msg.sender] != 0) {
                        count = int16(_playersBySeat.length);
                }
                return count;
        }

        // This is to get someone else's P2PID so you can talk to 'em
        function get_player_p2pid(uint16 seat) returns(string) {
                string memory p2pID = "";
                if (_playerSeatsPlus1[msg.sender] != 0) {
                        p2pID = _playersBySeat[seat].p2pID;
                }
                return p2pID;
        }

}