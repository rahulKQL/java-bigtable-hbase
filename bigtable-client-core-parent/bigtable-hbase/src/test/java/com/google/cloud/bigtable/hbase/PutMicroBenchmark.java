/*
 * Copyright 2015 Google Inc. All Rights Reserved.
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
package com.google.cloud.bigtable.hbase;

import static com.google.cloud.bigtable.hbase.BigtableOptionsFactory.CUSTOM_USER_AGENT_KEY;

import com.google.bigtable.v2.MutateRowRequest;
import com.google.bigtable.v2.MutateRowResponse;
import com.google.cloud.bigtable.data.v2.internal.RequestContext;
import com.google.cloud.bigtable.grpc.BigtableDataClient;
import com.google.cloud.bigtable.grpc.BigtableDataGrpcClient;
import com.google.cloud.bigtable.grpc.BigtableSession;
import com.google.cloud.bigtable.grpc.BigtableSessionSharedThreadPools;
import com.google.cloud.bigtable.grpc.io.ChannelPool;
import com.google.cloud.bigtable.hbase.adapters.HBaseRequestAdapter;
import com.google.cloud.bigtable.hbase.wrappers.BigtableHBaseSettings;
import com.google.cloud.bigtable.hbase.wrappers.classic.BigtableHBaseClassicSettings;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;

// TODO(rahulkql): If possible move this class to bigtable-1.x-benchmark.
public class PutMicroBenchmark {
  static final int NUM_CELLS = 10;
  private static final byte[] COLUMN_FAMILY = Bytes.toBytes("test_family");
  private static final int REAL_CHANNEL_PUT_COUNT = 100;
  private static final int FAKE_CHANNEL_PUT_COUNT = 100_000;
  private static final int VALUE_SIZE = 100;
  private static BigtableHBaseSettings settings;
  private static RequestContext requestContext;

  public static void main(String[] args) throws Exception {
    String projectId = args.length > 0 ? args[0] : "project";
    String instanceId = args.length > 1 ? args[1] : "instanceId";
    String tableId = args.length > 2 ? args[2] : "table";

    Configuration configuration = BigtableConfiguration.configure(projectId, instanceId);
    configuration.set(CUSTOM_USER_AGENT_KEY, "put_microbenchmark");
    settings = BigtableHBaseSettings.create(configuration);

    boolean useRealConnection = args.length >= 2;
    int putCount = useRealConnection ? REAL_CHANNEL_PUT_COUNT : FAKE_CHANNEL_PUT_COUNT;
    HBaseRequestAdapter hbaseAdapter =
        new HBaseRequestAdapter(settings, TableName.valueOf(tableId));
    requestContext = RequestContext.create(settings.getProjectId(), settings.getInstanceId(), "");

    testCreatePuts(10_000);

    Put put = createPut();
    System.out.println(
        String.format(
            "Put size: %d, proto size: %d",
            put.heapSize(), hbaseAdapter.adapt(put).toProto(requestContext).getSerializedSize()));
    run(hbaseAdapter, put, getChannelPool(useRealConnection), putCount);
  }

  protected static ManagedChannel getChannelPool(final boolean useRealConnection)
      throws IOException, GeneralSecurityException {
    if (useRealConnection) {
      return BigtableSession.createChannelPool(
          settings.getDataHost(), ((BigtableHBaseClassicSettings) settings).getBigtableOptions());
    } else {
      return new ChannelPool(createFakeChannels(), 1);
    }
  }

  protected static ChannelPool.ChannelFactory createFakeChannels() {
    final ManagedChannel channel = createFakeChannel();
    return new ChannelPool.ChannelFactory() {
      @Override
      public ManagedChannel create() throws IOException {
        return channel;
      }
    };
  }

  private static void createRandomData() {
    DataGenerationHelper dataHelper = new DataGenerationHelper();
    dataHelper.randomData("testrow-");

    for (int i = 0; i < NUM_CELLS; ++i) {
      dataHelper.randomData("testQualifier-");
      dataHelper.randomData("testValue-");
    }
  }

  private static Put createPut() {
    DataGenerationHelper dataHelper = new DataGenerationHelper();
    return createPuts(
        dataHelper.randomData("testrow-"),
        dataHelper.randomData("testQualifier-", NUM_CELLS),
        dataHelper.randomData("testValue-", NUM_CELLS));
  }

  protected static Put createPuts(byte[] rowKey, byte[][] quals, byte[][] values) {
    Put put = new Put(rowKey);
    for (int i = 0; i < NUM_CELLS; ++i) {
      put.addImmutable(COLUMN_FAMILY, quals[i], values[i]);
    }
    return put;
  }

  protected static void run(
      final HBaseRequestAdapter hbaseAdapter, final Put put, ManagedChannel cp, final int putCount)
      throws InterruptedException {
    final BigtableDataClient client =
        new BigtableDataGrpcClient(
            cp,
            BigtableSessionSharedThreadPools.getInstance().getRetryExecutor(),
            ((BigtableHBaseClassicSettings) settings).getBigtableOptions());

    Runnable r1 =
        new Runnable() {
          @Override
          public void run() {
            long start = System.nanoTime();
            for (int i = 0; i < putCount; i++) {
              client.mutateRow(hbaseAdapter.adapt(put).toProto(requestContext));
            }
            print("constantly adapted", start, putCount);
          }
        };

    Runnable r2 =
        new Runnable() {
          @Override
          public void run() {
            long start = System.nanoTime();
            final MutateRowRequest adapted = hbaseAdapter.adapt(put).toProto(requestContext);
            for (int i = 0; i < putCount; i++) {
              client.mutateRow(adapted);
            }
            print("preadapted", start, putCount);
          }
        };

    r1.run();
    r2.run();

    int roundCount = 20;

    System.out.println("====== Running serially");
    serialRun("constantly adapted", putCount, r1, roundCount);
    serialRun("pre adapted", putCount, r2, roundCount);

    System.out.println("====== Running in parallel");
    runParallel("constantly adapted", r1, putCount, roundCount);
    runParallel("pre adapted", r2, putCount, roundCount);

    System.out.println("====== Running serially");
    serialRun("constantly adapted", putCount, r1, roundCount);
    serialRun("pre adapted", putCount, r2, roundCount);
  }

  protected static void runParallel(
      final String key, Runnable r, final int putCount, int roundCount)
      throws InterruptedException {
    ExecutorService e = Executors.newFixedThreadPool(roundCount);
    long start = System.nanoTime();
    for (int i = 0; i < roundCount; i++) {
      e.execute(r);
    }
    e.shutdown();
    e.awaitTermination(10, TimeUnit.HOURS);
    print(key, start, putCount * roundCount);
  }

  private static void testCreatePuts(int putCount) {
    long start = System.nanoTime();
    for (int i = 0; i < putCount; i++) {
      createRandomData();
    }
    print("Created random data", start, putCount);

    DataGenerationHelper dataHelper = new DataGenerationHelper();
    byte[] key = dataHelper.randomData("testrow-");
    byte[][] quals = dataHelper.randomData("testQualifier-", NUM_CELLS);
    byte[][] vals = dataHelper.randomData("testValue-", NUM_CELLS, VALUE_SIZE);

    for (int j = 0; j < 20; j++) {
      start = System.nanoTime();
      for (int i = 0; i < putCount; i++) {
        createPuts(key, quals, vals);
      }
      print("Created Puts", start, putCount);
    }
  }

  private static void serialRun(String key, final int putCount, Runnable r, int roundCount) {
    long start = System.nanoTime();
    for (int i = 0; i < roundCount; i++) {
      r.run();
    }
    print(key, start, putCount * roundCount);
  }

  private static void print(String key, long startTimeNanos, int count) {
    long totalTime = System.nanoTime() - startTimeNanos;

    System.out.printf(
        "%s, Put %,d in %d ms.  %,d nanos/put.  %,f put/sec",
        key, count, totalTime / 1000000, totalTime / count, count * 1000000000.0 / totalTime);
    System.out.println();
  }

  static final ManagedChannel channel =
      new ManagedChannel() {
        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
            MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
          return createNewCall();
        }

        @Override
        public String authority() {
          return null;
        }

        @Override
        public ManagedChannel shutdownNow() {
          return null;
        }

        @Override
        public ManagedChannel shutdown() {
          return null;
        }

        @Override
        public boolean isTerminated() {
          return false;
        }

        @Override
        public boolean isShutdown() {
          return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
          return false;
        }
      };

  private static ManagedChannel createFakeChannel() {
    return channel;
  }

  protected static <RequestT, ResponseT> ClientCall<RequestT, ResponseT> createNewCall() {
    return new ClientCall<RequestT, ResponseT>() {

      private ClientCall.Listener<ResponseT> responseListener;

      @Override
      public void start(ClientCall.Listener<ResponseT> responseListener, Metadata headers) {
        this.responseListener = responseListener;
      }

      @Override
      public void request(int numMessages) {}

      @Override
      public void halfClose() {}

      @Override
      @SuppressWarnings("unchecked")
      public void sendMessage(RequestT message) {
        responseListener.onMessage((ResponseT) MutateRowResponse.getDefaultInstance());
        responseListener.onClose(Status.OK, null);
      }

      @Override
      public void cancel(String message, Throwable cause) {}
    };
  }
}
