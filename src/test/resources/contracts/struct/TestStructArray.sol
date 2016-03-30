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

    function TestStructArray() {
        uint wifeId = addPerson('Angelina Jolie ', 29, Gender.FEMALE);
        uint husbandId = addPerson('Brad Pitt', 28, Gender.MALE);

        addMarriage(wifeId, husbandId);
    }

    function addMarriage(uint wifeId, uint husbandId) returns (uint) {
        return register.push(Marriage(wifeId, husbandId, now));
    }

    function addPerson(string name, uint age, Gender gender) returns (uint){
        return persons.push(Person(name, age, gender));
    }
}