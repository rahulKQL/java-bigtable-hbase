/*
 * Copyright 2019 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.bigtable.beam.it;

import com.google.bigtable.repackaged.com.google.common.base.Preconditions;
import com.google.bigtable.v2.Row;
import com.google.cloud.bigtable.beam.CloudBigtableIO;
import com.google.cloud.bigtable.beam.CloudBigtableScanConfiguration;
import com.google.cloud.bigtable.beam.CloudBigtableTableConfiguration;
import com.google.cloud.bigtable.config.BigtableOptions;
import com.google.cloud.bigtable.config.Logger;

import com.google.cloud.bigtable.hbase.BigtableConfiguration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.beam.runners.dataflow.DataflowRunner;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.Read;
import org.apache.beam.sdk.io.gcp.bigtable.BigtableIO;
import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.FirstKeyOnlyFilter;
import org.apache.hadoop.hbase.shaded.org.apache.commons.lang.RandomStringUtils;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.bigtable.repackaged.com.google.cloud.bigtable.config.BigtableOptions.BIGTABLE_ADMIN_HOST_DEFAULT;
import static com.google.bigtable.repackaged.com.google.cloud.bigtable.config.BigtableOptions.BIGTABLE_BATCH_DATA_HOST_DEFAULT;
import static com.google.cloud.bigtable.hbase.BigtableOptionsFactory.BIGTABLE_ADMIN_HOST_KEY;
import static com.google.cloud.bigtable.hbase.BigtableOptionsFactory.BIGTABLE_HOST_KEY;
import static com.google.cloud.bigtable.hbase.BigtableOptionsFactory.INSTANCE_ID_KEY;
import static com.google.cloud.bigtable.hbase.BigtableOptionsFactory.PROJECT_ID_KEY;

/**
 * Integration test for Beam Dataflow
 */
@RunWith(JUnit4.class)
public class CloudBigtableBeamITTest {

  private final Logger LOG = new Logger(getClass());

  private static final String STAGING_LOCATION_KEY = "dataflowStagingLocation";
  private static final String ZONE_ID_KEY = "dataflowZoneId";

  private static final String projectId = System.getProperty(PROJECT_ID_KEY);
  private static final String instanceId = System.getProperty(INSTANCE_ID_KEY);
  private static final String stagingLocation = System.getProperty(STAGING_LOCATION_KEY);
  private static final String zoneId = System.getProperty(ZONE_ID_KEY);
  private static final String workerMachineType =
      System.getProperty("workerMachineType", "n1" + "-standard-8");
  private static final String dataEndpoint = System.getProperty(BIGTABLE_HOST_KEY, BIGTABLE_BATCH_DATA_HOST_DEFAULT);
  private static final String adminEndpoint = System.getProperty(
      BIGTABLE_ADMIN_HOST_KEY, BIGTABLE_ADMIN_HOST_DEFAULT);

  private static final String TABLE_NAME_STR =
      System.getProperty("tableName", "BeamCloudBigtableIOIntegrationTest");
  private static final TableName TABLE_NAME = TableName.valueOf(TABLE_NAME_STR);


  private static byte[] FAMILY = Bytes.toBytes("test-family");
  private static byte[] QUALIFIER = Bytes.toBytes("test-qualifier");

  private static final int CELL_SIZE = Integer.getInteger("cell_size", 1_000);
  private static final long TOTAL_ROW_COUNT = Integer.getInteger("total_row_count", 1_000_000);
  private static final int PREFIX_COUNT = Integer.getInteger("prefix_count", 1_000);

  @BeforeClass
  public static void setUpConfiguration() {
    Preconditions.checkArgument(stagingLocation != null, "Set -D" + STAGING_LOCATION_KEY + ".");
    Preconditions.checkArgument(zoneId != null, "Set -D" + ZONE_ID_KEY + ".");
    Preconditions.checkArgument(projectId != null, "Set -D" + PROJECT_ID_KEY + ".");
    Preconditions.checkArgument(instanceId != null, "Set -D" + INSTANCE_ID_KEY + ".");
  }

  @Before
  public void setUp() throws IOException {
    Configuration config = BigtableConfiguration.configure(projectId, instanceId);
    config.set(BIGTABLE_HOST_KEY, dataEndpoint);
    config.set(BIGTABLE_ADMIN_HOST_KEY, adminEndpoint);

    try(Connection conn = BigtableConfiguration.connect(config); Admin admin = conn.getAdmin()){
      if (admin.tableExists(TABLE_NAME)) {
        admin.deleteTable(TABLE_NAME );
      }
      admin.createTable(new HTableDescriptor(TABLE_NAME)
          .addFamily(new HColumnDescriptor(FAMILY)));
      LOG.info("Created a table to perform batching: %s", TABLE_NAME);
    }
  }

  private static final DoFn<String, Mutation> WRITE_ONE_TENTH_PERCENT =
      new DoFn<String, Mutation>() {

        private static final long serialVersionUID = 1L;
        private Counter rowCounter = Metrics.counter(CloudBigtableBeamITTest.class, "sent_puts");

        @ProcessElement
        public void processElement(ProcessContext context) {
          String prefix = context.element() + "_";
          int max = (int) (TOTAL_ROW_COUNT / PREFIX_COUNT);
          for (int i = 0; i < max; i++) {
            rowCounter.inc();
            context.output(new Put(Bytes.toBytes(prefix + i))
                .addColumn(FAMILY, QUALIFIER, createRandomValue()));
          }
        }
      };

  private Pipeline writeToBigtable(){
    DataflowPipelineOptions options = createOptions();
    options.setAppName("test-write-to-bigtable-" + System.currentTimeMillis());
    LOG.info("Started writeToBigtable test with jobName as: %s", options.getAppName());

    CloudBigtableTableConfiguration config =
        new CloudBigtableTableConfiguration.Builder()
            .withProjectId(projectId)
            .withInstanceId(instanceId)
            .withTableId(TABLE_NAME.getNameAsString())
            .withConfiguration(BIGTABLE_ADMIN_HOST_KEY, adminEndpoint)
            .withConfiguration(BIGTABLE_HOST_KEY, dataEndpoint)
            .build();

    Pipeline p = Pipeline.create(options);

    List<String> keys = new ArrayList<>();
    for (int i = 0; i < PREFIX_COUNT; i++) {
      keys.add(RandomStringUtils.randomAlphanumeric(10));
    }

    p.apply("Keys", Create.of(keys))
        .apply("Create Puts", ParDo.of(WRITE_ONE_TENTH_PERCENT))
        .apply("Write to BT", CloudBigtableIO.writeToTable(config));
    return p;
  }

  private Pipeline readFromBigtable(){
    PipelineOptions options = createOptions();
    options.setJobName("test-read-from-bigtable-" + System.currentTimeMillis());
    LOG.info("Started readFromBigtable test with jobName as: %s", options.getJobName());

    Scan scan = new Scan();
    scan.setFilter(new FirstKeyOnlyFilter());

    CloudBigtableScanConfiguration config =
        new CloudBigtableScanConfiguration.Builder()
            .withProjectId(projectId)
            .withInstanceId(instanceId)
            .withTableId(TABLE_NAME.getNameAsString())
            .withScan(scan)
            .withConfiguration(BIGTABLE_ADMIN_HOST_KEY, adminEndpoint)
            .withConfiguration(BIGTABLE_HOST_KEY, dataEndpoint).build();

    Pipeline p = Pipeline.create(options);
    PCollection<Long> count = p
        .apply("Read from BT", Read.from(CloudBigtableIO.read(config)))
        .apply("Count", Count.<Result>globally());

    PAssert.thatSingleton(count).isEqualTo(TOTAL_ROW_COUNT);
    return p;
  }

  private Pipeline readFromBigtableIO(){
    PipelineOptions pipelineOptions = createOptions();
    pipelineOptions.setJobName("test-read-using-bigtableIO-" + System.currentTimeMillis());
    LOG.info("Started readFromBigtableIO test with jobName as: %s", pipelineOptions.getJobName());

    BigtableOptions options = new BigtableOptions.Builder()
        .setProjectId(projectId)
        .setInstanceId(instanceId)
        .setDataHost(dataEndpoint)
        .setAdminHost(adminEndpoint)
        .build();
    BigtableIO.Read read = BigtableIO.read().withBigtableOptions(options)
        .withTableId(TABLE_NAME.getNameAsString());

    Pipeline p = Pipeline.create(pipelineOptions);

    PCollection<Long> count = p
        .apply("Read from BT", read)
        .apply("Count", Count.<Row> globally());

    PAssert.thatSingleton(count).isEqualTo(TOTAL_ROW_COUNT);
    return p;
  }

  @Test
  public void testBeamReadAndWrite() throws Exception {
    // created Executors for read jobs to run in parallel.
    ExecutorService service = Executors.newCachedThreadPool();
    try {
      Assert.assertTrue(verifyPipeline(writeToBigtable()));

      // Submitted read job to thread pool.
      Future<Boolean> bigtableScanStatus = service.submit(() -> verifyPipeline(readFromBigtable()));

      Assert.assertTrue(bigtableScanStatus.get());
    } finally {
      service.shutdown();
      service.awaitTermination(8, TimeUnit.MINUTES);
    }
  }

// ---- Helper class to perform test.

  /** Verify the pipeline job is completed successfully with DONE status. */
  private Boolean verifyPipeline(Pipeline p) {
    try {
      PipelineResult.State state = p.run().waitUntilFinish();
      Assert.assertEquals(PipelineResult.State.DONE, state);
      return true;
    } catch (Exception ex) {
      ex.printStackTrace();
      throw new AssertionError("Exception occurred while pipeline execution");
    }
  }

  private static byte[] createRandomValue(){
    byte[] bytes = new byte[CELL_SIZE];
    new Random().nextBytes(bytes);
    return bytes;
  }

  private DataflowPipelineOptions createOptions() {
    DataflowPipelineOptions options = PipelineOptionsFactory.as(DataflowPipelineOptions.class);
    options.setProject(projectId);
    options.setZone(zoneId);
    options.setStagingLocation(stagingLocation + "/stage");
    options.setTempLocation(stagingLocation + "/temp");
    options.setRunner(DataflowRunner.class);
    options.setWorkerMachineType(workerMachineType);
    return options;
  }
}
