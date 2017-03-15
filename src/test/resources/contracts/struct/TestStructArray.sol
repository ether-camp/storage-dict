contract TestStructArray {

    enum Gender {
        MALE, FEMALE
    }

    struct Person {
        string name;
        uint age;
        Gender gender;
    }

    struct Marriage {
        uint wife;
        uint husband;
        uint marriageDate;
    }

    Person[] persons;
    Marriage[] register;

    function addMarriage(uint wifeId, uint husbandId) returns (uint id) {
        return register.push(Marriage(wifeId, husbandId, now)) - 1;
    }

    function addPerson(string name, uint age, Gender gender) returns (uint id){
        return persons.push(Person(name, age, gender)) - 1;
    }
}