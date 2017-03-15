package com.ethercamp.contrdata;

/**
 * Created by Anton Nashatyrev on 10.09.2015.
 */
public class StorageDictionaryHandlerTest {
/*

    private static final DataWord OWNER_ADDRESS = new DataWord(0xff);

    private static void vmSha3Notify(String in, String out, StorageDictionaryVmHook.Handler handler) {
        byte[] decoded = Hex.decode(in);
        byte[] encoded = Hex.decode(out);
        assertTrue(Arrays.equals(encoded, sha3(decoded)));

        handler.onSha3(decoded);

        Sha3Index.Entry entry = handler.getSha3Index().get(encoded);
        assertTrue(Arrays.equals(decoded, entry.getInput()));
        assertTrue(Arrays.equals(encoded, entry.getOutput()));
    }

    private static void vmSStoreNotify(String key, String value, StorageDictionaryVmHook.Handler handler) {
        handler.onSStore(new DataWord(key), new DataWord(value));
    }

    @Test
    public void test1() {
        StorageDictionaryVmHook.Handler handler = StorageDictionaryVmHook.Handler.forContract(OWNER_ADDRESS.getLast20Bytes());

        vmSha3Notify("00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000004",
                "abd6e7cb50984ff9c2f3e18a2660c3353dadf4e3291deeb275dae2cd1e44fe05", handler);
        vmSha3Notify("abd6e7cb50984ff9c2f3e18a2660c3353dadf4e3291deeb275dae2cd1e44fe05", "210afe6ebef982fa193bb4e17f9f236cdf09af7788627b5d54d9e3e4b100021b", handler);

        vmSStoreNotify("210afe6ebef982fa193bb4e17f9f236cdf09af7788627b5d54d9e3e4b100021b", "3137326142713267314b38327174626745755876384b63443342453531346258", handler);
        vmSStoreNotify("210afe6ebef982fa193bb4e17f9f236cdf09af7788627b5d54d9e3e4b100021c", "3772000000000000000000000000000000000000000000000000000000000000", handler);
        vmSStoreNotify("abd6e7cb50984ff9c2f3e18a2660c3353dadf4e3291deeb275dae2cd1e44fe05", "0000000000000000000000000000000000000000000000000000000000000022", handler);
        vmSStoreNotify("0000000000000000000000000000000000000000000000000000000000000003", "0000000000000000000000000000000000000000000000000000000000000002", handler);
        vmSStoreNotify("abd6e7cb50984ff9c2f3e18a2660c3353dadf4e3291deeb275dae2cd1e44fe06", "000000000000000000000000000000000000000000000000016345785d8a0000", handler);
        vmSStoreNotify("abd6e7cb50984ff9c2f3e18a2660c3353dadf4e3291deeb275dae2cd1e44fe07", "0000000000000000000000000d82cd113dc35ddda93f38166cd5cde8b88e36a1", handler);
    }

    @Test
    public void test2() {
        StorageDictionaryVmHook.Handler handler = StorageDictionaryVmHook.Handler.forContract(OWNER_ADDRESS.getLast20Bytes());

        vmSha3Notify("2153b9b2b56bb29c21cf47fe64fd2abdd7171b14cca48560f6f6cf294a1a5c520000000000000000000000000000000000000000000000000000000000000103",
                "ffd874e59055f4f3dfa2a72e56b6998ed34dc01ebcf35d9ab673308b9f41fc7f", handler);

        vmSStoreNotify("ffd874e59055f4f3dfa2a72e56b6998ed34dc01ebcf35d9ab673308b9f41fc7f", "3137326142713267314b38327174626745755876384b63443342453531346258", handler);
        vmSStoreNotify("ffd874e59055f4f3dfa2a72e56b6998ed34dc01ebcf35d9ab673308b9f41fc80", "3137326142713267314b38327174626745755876384b63443342453531346258", handler);
        vmSStoreNotify("ffd874e59055f4f3dfa2a72e56b6998ed34dc01ebcf35d9ab673308b9f41fc81", "3137326142713267314b38327174626745755876384b63443342453531346258", handler);
   }
*/
}

