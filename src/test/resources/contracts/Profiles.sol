contract Profiles {

  enum Gender {
    MALE,
    FEMALE
  }

  struct Profile {
    string alias;
    uint8 age;
    Gender gender;
    bool resident;
    mapping(uint => Profile) friends;
  }

  mapping(address => Profile) profileByAddress;
  Profile[] profiles;
  Profile profile;
  mapping(address => uint) counterByAddress;
  mapping(address => Profile[]) register;

  function addMale(string alias, uint8 age) returns (address key){
        profileByAddress[msg.sender] = Profile(alias, age, Gender.MALE, false);
        return msg.sender;
  }

  function addFemale(string alias, uint8 age) returns (address key){
        profileByAddress[msg.sender] = Profile(alias, age, Gender.FEMALE, false);
        return msg.sender;
  }

  function toggleResidence(address profileAddress) returns (bool resident) {
      return profileByAddress[profileAddress].resident = !profileByAddress[profileAddress].resident;
  }

}
