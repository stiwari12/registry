/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hortonworks.schemaregistry.samples.avro;

import com.hortonworks.registries.schemaregistry.SchemaFieldQuery;
import com.hortonworks.registries.schemaregistry.SchemaMetadata;
import com.hortonworks.registries.schemaregistry.SchemaProvider;
import com.hortonworks.registries.schemaregistry.SchemaVersion;
import com.hortonworks.registries.schemaregistry.SchemaVersionInfo;
import com.hortonworks.registries.schemaregistry.SchemaVersionKey;
import com.hortonworks.registries.schemaregistry.SerDesInfo;
import com.hortonworks.registries.schemaregistry.avro.AvroSchemaProvider;
import com.hortonworks.registries.schemaregistry.avro.AvroSnapshotDeserializer;
import com.hortonworks.registries.schemaregistry.avro.AvroSnapshotSerializer;
import com.hortonworks.registries.schemaregistry.client.SchemaRegistryClient;
import com.hortonworks.registries.schemaregistry.serde.SnapshotDeserializer;
import com.hortonworks.registries.schemaregistry.serde.SnapshotSerializer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.io.IOUtils;
import org.mortbay.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 *
 */
public class SampleSchemaRegistryApplication {
    private static final Logger LOG = LoggerFactory.getLogger(SampleSchemaRegistryApplication.class);
    private final Map<String, Object> config;
    private final SchemaRegistryClient schemaRegistryClient;

    public SampleSchemaRegistryApplication(Map<String, Object> config) {
        this.config = config;
        schemaRegistryClient = new SchemaRegistryClient(config);
    }

    public void runSchemaApis() throws Exception {

        String schemaFileName = "/device.avsc";
        String schema1 = getSchema(schemaFileName);
        SchemaMetadata schemaMetadata = createSchemaMetadata("com.hwx.schemas.sample-" + System.currentTimeMillis());

        // registering a new schema
        Integer v1 = schemaRegistryClient.addSchemaVersion(schemaMetadata, new SchemaVersion(schema1, "Initial version of the schema"));
        LOG.info("Registered schema [{}] and returned version [{}]", schema1, v1);

        // adding a new version of the schema
        String schema2 = getSchema("/device-next.avsc");
        SchemaVersion schemaInfo2 = new SchemaVersion(schema2, "second version");
        Integer v2 = schemaRegistryClient.addSchemaVersion(schemaMetadata, schemaInfo2);
        LOG.info("Registered schema [{}] and returned version [{}]", schema2, v2);

        //adding same schema returns the earlier registered version
        Integer version = schemaRegistryClient.addSchemaVersion(schemaMetadata, schemaInfo2);
        LOG.info("");

        // get a specific version of the schema
        String schemaName = schemaMetadata.getName();
        SchemaVersionInfo schemaVersionInfo = schemaRegistryClient.getSchemaVersionInfo(new SchemaVersionKey(schemaName, v2));

        // get latest version of the schema
        SchemaVersionInfo latest = schemaRegistryClient.getLatestSchemaVersionInfo(schemaName);
        LOG.info("Latest schema with schema key [{}] is : [{}]", schemaMetadata, latest);

        // get all versions of the schema
        Collection<SchemaVersionInfo> allVersions = schemaRegistryClient.getAllVersions(schemaName);
        LOG.info("All versions of schema key [{}] is : [{}]", schemaMetadata, allVersions);

        // finding schemas containing a specific field
        SchemaFieldQuery md5FieldQuery = new SchemaFieldQuery.Builder().name("md5").build();
        Collection<SchemaVersionKey> md5SchemaVersionKeys = schemaRegistryClient.findSchemasByFields(md5FieldQuery);
        LOG.info("Schemas containing field query [{}] : [{}]", md5FieldQuery, md5SchemaVersionKeys);

        SchemaFieldQuery txidFieldQuery = new SchemaFieldQuery.Builder().name("txid").build();
        Collection<SchemaVersionKey> txidSchemaVersionKeys = schemaRegistryClient.findSchemasByFields(txidFieldQuery);
        LOG.info("Schemas containing field query [{}] : [{}]", txidFieldQuery, txidSchemaVersionKeys);

    }

    private void runAvroSerDesApis() throws IOException {
        //using builtin avro serializer/deserializer
        AvroSnapshotSerializer avroSnapshotSerializer = new AvroSnapshotSerializer();
        avroSnapshotSerializer.init(config);
        AvroSnapshotDeserializer avroSnapshotDeserializer = new AvroSnapshotDeserializer();
        avroSnapshotDeserializer.init(config);

        Object deviceObject = createGenericRecordForDevice("/device.avsc");

        SchemaMetadata schemaMetadata = createSchemaMetadata("avro-serializer-schema-" + System.currentTimeMillis());
        byte[] serializedData = avroSnapshotSerializer.serialize(deviceObject, schemaMetadata);
        Object deserializedObj = avroSnapshotDeserializer.deserialize(new ByteArrayInputStream(serializedData), schemaMetadata, null);

        Log.info("Serialized and deserialized objects are equal: [{}] ", deviceObject.equals(deserializedObj));
    }

    protected Object createGenericRecordForDevice(String schemaFileName) throws IOException {
        Schema schema = new Schema.Parser().parse(getSchema(schemaFileName));

        GenericRecord avroRecord = new GenericData.Record(schema);
        long now = System.currentTimeMillis();
        avroRecord.put("xid", now);
        avroRecord.put("name", "foo-" + now);
        avroRecord.put("version", new Random().nextInt());
        avroRecord.put("timestamp", now);

        return avroRecord;
    }

    private SchemaMetadata createSchemaMetadata(String name) {
        return new SchemaMetadata.Builder(name)
                .type(AvroSchemaProvider.TYPE)
                .schemaGroup("sample-group")
                .description("Sample schema")
                .compatibility(SchemaProvider.Compatibility.BACKWARD)
                .build();
    }

    private String getSchema(String schemaFileName) throws IOException {
        InputStream schemaResourceStream = SampleSchemaRegistryApplication.class.getResourceAsStream(schemaFileName);
        if (schemaResourceStream == null) {
            throw new IllegalArgumentException("Given schema file [" + schemaFileName + "] does not exist");
        }

        return IOUtils.toString(schemaResourceStream, "UTF-8");
    }


    public void runSerDesApi() throws Exception {
        // upload jar file
        String serdesJarName = "/samples-serdes.jar";
        InputStream serdesJarInputStream = SampleSchemaRegistryApplication.class.getResourceAsStream(serdesJarName);
        if (serdesJarInputStream == null) {
            throw new RuntimeException("Jar " + serdesJarName + " could not be loaded");
        }
        String fileId = schemaRegistryClient.uploadFile(serdesJarInputStream);

        SchemaMetadata schemaMetadata = createSchemaMetadata("serdes-device-" + System.currentTimeMillis());
        Integer v1 = schemaRegistryClient.addSchemaVersion(schemaMetadata,
                new SchemaVersion(getSchema("/device.avsc"),
                        "Initial version of the schema"));

        // register serializer
        Long serializerId = registerSimpleSerializer(fileId);

        // register deserializer
        Long deserializerId = registerSimpleDeserializer(fileId);

        // map serializer and deserializer with schemakey
        // for each schema, one serializer/deserializer is sufficient unless someone want to maintain multiple implementations of serializers/deserializers
        String schemaName = schemaMetadata.getName();
        schemaRegistryClient.mapSchemaWithSerDes(schemaName, serializerId);
        schemaRegistryClient.mapSchemaWithSerDes(schemaName, deserializerId);

        SnapshotSerializer<Object, byte[], SchemaMetadata> snapshotSerializer = getSnapshotSerializer(schemaMetadata);
        String payload = "Random text: " + new Random().nextLong();
        byte[] serializedBytes = snapshotSerializer.serialize(payload, schemaMetadata);

        SnapshotDeserializer<byte[], Object, SchemaMetadata, Integer> snapshotdeserializer = getSnapshotDeserializer(schemaMetadata);
        Object deserializedObject = snapshotdeserializer.deserialize(serializedBytes, schemaMetadata, null);

        LOG.info("Given payload and deserialized object are equal: " + payload.equals(deserializedObject));
    }

    private Long registerSimpleSerializer(String fileId) {
        String simpleSerializerClassName = "com.hortonworks.schemaregistry.samples.serdes.SimpleSerializer";
        SerDesInfo serializerInfo = new SerDesInfo.Builder()
                .name("simple-serializer")
                .description("simple serializer")
                .fileId(fileId)
                .className(simpleSerializerClassName)
                .buildSerializerInfo();
        return schemaRegistryClient.addSerializer(serializerInfo);
    }

    private Long registerSimpleDeserializer(String fileId) {
        String simpleDeserializerClassName = "com.hortonworks.schemaregistry.samples.serdes.SimpleDeserializer";
        SerDesInfo deserializerInfo = new SerDesInfo.Builder()
                .name("simple-deserializer")
                .description("simple deserializer")
                .fileId(fileId)
                .className(simpleDeserializerClassName)
                .buildDeserializerInfo();
        return schemaRegistryClient.addDeserializer(deserializerInfo);
    }

    private SnapshotDeserializer<byte[], Object, SchemaMetadata, Integer> getSnapshotDeserializer(SchemaMetadata schemaMetadata) {
        Collection<SerDesInfo> serializers = schemaRegistryClient.getDeserializers(schemaMetadata.getName());
        if (serializers.isEmpty()) {
            throw new RuntimeException("Serializer for schemaKey:" + schemaMetadata + " must exist");
        }
        SerDesInfo serdesInfo = serializers.iterator().next();
        return schemaRegistryClient.createDeserializerInstance(serdesInfo);
    }

    private SnapshotSerializer<Object, byte[], SchemaMetadata> getSnapshotSerializer(SchemaMetadata schemaMetadata) {
        Collection<SerDesInfo> serializers = schemaRegistryClient.getSerializers(schemaMetadata.getName());
        if (serializers.isEmpty()) {
            throw new RuntimeException("Serializer for schemaKey:" + schemaMetadata + " must exist");
        }
        SerDesInfo serdesInfo = serializers.iterator().next();
        return schemaRegistryClient.createSerializerInstance(serdesInfo);
    }

    public static void main(String[] args) throws Exception {
        String schemaRegistryUrl = System.getProperty(SchemaRegistryClient.Options.SCHEMA_REGISTRY_URL, "http://localhost:8080/api/v1");
        Map<String, Object> config = createConfig(schemaRegistryUrl);
        SampleSchemaRegistryApplication sampleSchemaRegistryApplication = new SampleSchemaRegistryApplication(config);

        sampleSchemaRegistryApplication.runSchemaApis();

        sampleSchemaRegistryApplication.runSerDesApi();

        sampleSchemaRegistryApplication.runAvroSerDesApis();
    }

    private static Map<String, Object> createConfig(String schemaRegistryUrl) {
        Map<String, Object> config = new HashMap<>();
        config.put(SchemaRegistryClient.Options.SCHEMA_REGISTRY_URL, schemaRegistryUrl);
        config.put(SchemaRegistryClient.Options.CLASSLOADER_CACHE_SIZE, 10);
        config.put(SchemaRegistryClient.Options.CLASSLOADER_CACHE_EXPIRY_INTERVAL_SECS, 5000L);
        config.put(SchemaRegistryClient.Options.SCHEMA_CACHE_SIZE, 1000);
        config.put(SchemaRegistryClient.Options.SCHEMA_CACHE_EXPIRY_INTERVAL_SECS, 60 * 60 * 1000L);
        return config;
    }

}
