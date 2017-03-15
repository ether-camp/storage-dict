contract Simple {

        enum ActionChoices {
                GoLeft,
                GoRight,
                GoStraight,
                SitStill
        }
        ActionChoices choice;
        ActionChoices constant defaultChoice = ActionChoices.GoRight;

        bool[35] array;
//        ActionChoices[3] array;

        function Simple() {

                array[1] = true;
                array[15] = true;
                array[33] = true;
        }
}