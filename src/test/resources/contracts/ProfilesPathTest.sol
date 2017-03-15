contract ProfilesPathTest {
  enum Gender {
    MALE,                                       // 0
    FEMALE                                      // 1
  }

  struct Profile {
    string alias;                               // 0
    uint8 age;                                  // 1
    Gender gender;                              // 2
    bool resident;                              // 3
    mapping(uint => Profile) friends;           // 4
  }

  mapping(address => Profile) profileByAddress; // 0
  Profile[] profiles;                           // 1
  Profile profile;                              // 2
  mapping(address => uint) counterByAddress;    // 3
  mapping(address => Profile[]) register;       // 4
}