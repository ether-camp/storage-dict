// import "std.sol";
// This si marker
// for contract not
// to be deployed to
// any environment
contract Abstract {}

contract owned is Abstract {
        address owner;

        function owned() {
                owner = msg.sender;
        }

        function changeOwner(address newOwner) onlyowner {
                owner = newOwner;
        }
        modifier onlyowner() {
                if (msg.sender == owner) _;
        }
}

contract mortal is Abstract, owned {
        function kill() onlyowner {
                if (msg.sender == owner) suicide(owner);
        }
}

contract NameReg is Abstract {
        function register(bytes32 name) {}

        function unregister() {}

        function addressOf(bytes32 name) constant returns(address addr) {}

        function nameOf(address addr) constant returns(bytes32 name) {}

        function kill() {}
}

contract NameRegAware is Abstract {
        function nameRegAddress() returns(address) {
                // return 0x0860a8008298322a142c09b528207acb5ab7effc;

                return 0x985509582b2c38010bfaa3c8d2be60022d3d00da; //frontier one
        }

        function named(bytes32 name) returns(address) {
                return NameReg(nameRegAddress()).addressOf(name);
        }
}

contract Named is Abstract, NameRegAware {
        function Named(bytes32 name) {
                NameReg(nameRegAddress()).register(name);
        }
}

// contract with util functions
contract Util is Abstract {
        // Converts 'string' to 'bytes32'
        function s2b(string s) internal returns(bytes32) {
                bytes memory b = bytes(s);
                uint r = 0;
                for (uint i = 0; i < 32; i++) {
                        if (i < b.length) {
                                r = r | uint(b[i]);
                        }
                        if (i < 31) r = r * 256;
                }
                return bytes32(r);
        }
}


contract ProjectKudos is owned, Named("ProjectKudos") {

        enum Status {
                InProgress,
                Finished
        }

        struct ProjectInfo {
                mapping(address => uint) kudosGiven;
                uint kudosTotal;
        }

        struct UserInfo {
                uint kudosLimit;
                uint kudosGiven;
                bool isJudge;
        }

        struct UserIndex {
                address[] projects;
                uint[] kudos;
                mapping(address => uint) kudosIdx;
        }

        Status status;
        mapping(address => ProjectInfo) projects;
        mapping(address => UserInfo) users;
        mapping(address => UserIndex) usersIndex;

        function ProjectKudos() {
                status = Status.InProgress;
        }

        function finish() onlyowner {
                status = Status.Finished;
        }

        function getKudosPerProject(address giver) constant returns(address[] projects, uint[] kudos) {
                UserIndex idx = usersIndex[giver];

                projects = idx.projects;
                kudos = idx.kudos;
        }

        function register(address addr, uint kudosLimit, bool isJudge) onlyowner {
                UserInfo user = users[addr];

                if (user.kudosLimit > 0) throw;

                user.kudosLimit = kudosLimit;
                user.isJudge = isJudge;
        }

        function getProjectKudos(address projectAddr) constant returns(uint) {
                ProjectInfo project = projects[projectAddr];
                return project.kudosTotal;
        }

        function getKudosLeft(address addr) constant returns(uint) {
                UserInfo user = users[addr];
                return user.kudosLimit - user.kudosGiven;
        }

        function getKudosLeftForProject(address addr, address projectAddr) constant returns(uint) {
                UserInfo user = users[addr];
                ProjectInfo project = projects[projectAddr];
                uint givenToProject = kudosGiven(user, project.kudosGiven[addr]);
                return user.kudosLimit - givenToProject;
        }

        function getKudosGiven(address addr) constant returns(uint) {
                UserInfo user = users[addr];
                return user.kudosGiven;
        }

        function giveKudos(address projectAddr, uint kudos) {

                if (status == Status.Finished) throw;

                UserInfo giver = users[msg.sender];

                if (giver.kudosLimit == 0) throw;

                ProjectInfo project = projects[projectAddr];

                uint kudosCount = kudosGiven(giver, project.kudosGiven[msg.sender]) + kudos;

                if (kudosCount <= giver.kudosLimit) {
                        giver.kudosGiven += kudos;
                        project.kudosTotal += kudos;
                        project.kudosGiven[msg.sender] += kudos;

                        // let's update index
                        UserIndex idx = usersIndex[msg.sender];
                        uint i = idx.kudosIdx[projectAddr];
                        if (i == 0) {
                                i = idx.projects.length;
                                idx.projects.length += 1;
                                idx.kudos.length += 1;
                                idx.projects[i] = projectAddr;
                                idx.kudosIdx[projectAddr] = i + 1;
                        } else {
                                i -= 1;
                        }

                        idx.kudos[i] = project.kudosGiven[msg.sender];
                }
        }

        function kudosGiven(UserInfo user, uint projectKudos) private returns(uint) {
                if (user.isJudge) {
                        return user.kudosGiven;
                } else {
                        return projectKudos;
                }
        }
}