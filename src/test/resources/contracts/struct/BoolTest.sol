contract BoolTest {

        struct Struct {
                uint number;
                bool flag;
                string str;
        }

        Struct singleStruct;

        bool theFirstBool;
        uint numberBetween;
        bool theLastBool;

        bool first;
        bool second;
        bool third;

        bool[] array;
        bool[2] staticArray;

        function BoolTest() {
                theFirstBool = true;
                numberBetween = 132;
                theLastBool = true;

                singleStruct = Struct(222, true, 'root level struct');
        }

        function setFirst(bool pFirst) {
                first = pFirst;
        }

        function setSecond(bool pSecond) {
                second = pSecond;
        }

        function setthird(bool pThird) {
                third = pThird;
        }

        function setAllArray(bool[] pArray) {
                array = pArray;
        }

        function setArrayWithPush(bool[] pArray) {
                for (var i = 0; i < pArray.length; i++) {
                        array.push(pArray[i]);
                }
        }

        function setAllStaticArray(bool[2] pArray) {
                staticArray = pArray;
        }

        function setStaticArraybyIndex(bool[2] pArray) {
                staticArray[0] = pArray[0];
                staticArray[1] = pArray[1];
        }
}