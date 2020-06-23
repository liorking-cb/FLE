package com.couchbase.fledemo;

import com.couchbase.client.deps.com.fasterxml.jackson.databind.ObjectMapper;
import com.couchbase.client.encryption.*;
import com.couchbase.client.java.*;
import com.couchbase.client.java.document.*;
import com.couchbase.client.java.env.*;
import com.couchbase.client.java.repository.annotation.*;
import java.nio.charset.Charset;
import java.util.logging.*;

public class FLE {

    public static class Person {
        @Id
        public String id = "johnd";
        public String name = "John Doe";
        @EncryptedField(provider = "AES256")
        public String password = "Top secret";
        @EncryptedField(provider = "AES256")
        public String creditCard = "1111-2222-3333-4444";
    }

    private static CryptoManager cryptoManager = new CryptoManager();

    // The following function set the Couchbase Environment variable (couchbaseEnv) with the
    // necessary settings for field level encryption
    private static void SetCryptoManager() {
        try {
            // Setting a key pair for the public key and private key.
            JceksKeyStoreProvider kp = new JceksKeyStoreProvider("secret");
            kp.publicKeyName("mypublickey");
            kp.storeKey("mypublickey", "@mysecretkey#9^5usdk39d&dlf)03ME".getBytes(Charset.forName("UTF-8")));
            kp.signingKeyName("myprivatekey");
            kp.storeKey("myprivatekey", "myauthpassword".getBytes(Charset.forName("UTF-8")));

            AES256CryptoProvider aes256CryptoProvider = new AES256CryptoProvider(kp);
            cryptoManager.registerProvider("AES256", aes256CryptoProvider);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args)  {
        try {
            //setting the crypto manager property. It will be used for the Couchbase environment setting.
            SetCryptoManager();
            Logger.getLogger("com.couchbase.client").setLevel(Level.WARNING);

            CouchbaseEnvironment couchbaseEnv = DefaultCouchbaseEnvironment.builder().cryptoManager(cryptoManager).build();
            Cluster cluster = CouchbaseCluster.create(couchbaseEnv, "localhost");
            cluster.authenticate("Administrator", "password");
            Bucket bucket = cluster.openBucket("travel-sample");

            Person person = new Person();
            ObjectMapper mapper = new ObjectMapper();

            System.out.println("Saving this document into the bucket:");
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(person));

            // "Upserting" the "person" object into the bucket
            bucket.repository().upsert(EntityDocument.create(person));

            // Reading the "raw" document, that was just written, from the bucket
            JsonDocument document2 = bucket.get(person.id);
            System.out.println("\nReading what Couchbase has stored: (Doc Id = " + person.id + ")");
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document2.content().toMap()));

            //Read the document into a Person object
            //NOTE: Couchbase will decrypt the encrypted fields (they are annotated with "@EncryptedField").
            EntityDocument<Person> stored = bucket.repository().get(person.id, Person.class);
            System.out.println("\nNow reading and decrypting the document:");
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(stored.content()));
        }
        catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}