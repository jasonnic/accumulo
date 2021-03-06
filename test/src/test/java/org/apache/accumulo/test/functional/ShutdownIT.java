/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.test.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.util.UtilWaitThread;
import org.apache.accumulo.server.util.Admin;
import org.apache.accumulo.test.TestIngest;
import org.apache.accumulo.test.TestRandomDeletes;
import org.apache.accumulo.test.VerifyIngest;
import org.junit.Test;

public class ShutdownIT extends ConfigurableMacIT {
  
  @Test(timeout = 3 * 60 * 1000)
  public void shutdownDuringIngest() throws Exception {
    Process ingest = cluster.exec(TestIngest.class, "-i", cluster.getInstanceName(), "-z", cluster.getZooKeepers(), "-u", "root", "-p", ROOT_PASSWORD, "--createTable");
    UtilWaitThread.sleep(100);
    assertEquals(0, cluster.exec(Admin.class, "stopAll").waitFor());
    ingest.destroy();
  }
  
  @Test(timeout = 2 * 60 * 1000)
  public void shutdownDuringQuery() throws Exception {
    assertEquals(0, cluster.exec(TestIngest.class, "-i", cluster.getInstanceName(), "-z", cluster.getZooKeepers(), "-u", "root","-p", ROOT_PASSWORD, "--createTable").waitFor());
    Process verify = cluster.exec(VerifyIngest.class, "-i", cluster.getInstanceName(), "-z", cluster.getZooKeepers(), "-u", "root", "-p", ROOT_PASSWORD);
    UtilWaitThread.sleep(100);
    assertEquals(0, cluster.exec(Admin.class, "stopAll").waitFor());
    verify.destroy();
  }
  
  @Test(timeout = 2 * 60 * 1000)
  public void shutdownDuringDelete() throws Exception {
    assertEquals(0, cluster.exec(TestIngest.class, "-i", cluster.getInstanceName(), "-z", cluster.getZooKeepers(), "-u", "root", "-p", ROOT_PASSWORD, "--createTable").waitFor());
    Process deleter = cluster.exec(TestRandomDeletes.class, "-i", cluster.getInstanceName(), "-z", cluster.getZooKeepers(), "-u", "root", "-p", ROOT_PASSWORD);
    UtilWaitThread.sleep(100);
    assertEquals(0, cluster.exec(Admin.class, "stopAll").waitFor());
    deleter.destroy();
  }

  
  @Test(timeout = 60 * 1000)
  public void shutdownDuringDeleteTable() throws Exception {
    final Connector c = getConnector();
    for (int i = 0; i < 10 ; i++) {
      c.tableOperations().create("table" + i);
    }
    final AtomicReference<Exception> ref = new AtomicReference<Exception>();
    Thread async = new Thread() {
      public void run() {
        try {
          for (int i = 0; i < 10; i++)
            c.tableOperations().delete("table" + i);
        } catch (Exception ex) {
          ref.set(ex);
        }
      }
    };
    async.start();
    UtilWaitThread.sleep(100);
    assertEquals(0, cluster.exec(Admin.class, "stopAll").waitFor());
    if (ref.get() != null)
      throw ref.get();
  }
  
  @Test(timeout = 4 * 60 * 1000)
  public void stopDuringStart() throws Exception {
    assertEquals(0, cluster.exec(Admin.class, "stopAll").waitFor());
  }
  
  @Test(timeout = 2 * 60 * 1000)
  public void adminStop() throws Exception {
    Connector c = getConnector();
    assertEquals(0, cluster.exec(TestIngest.class, "-i", cluster.getInstanceName(), "-z", cluster.getZooKeepers(), "-u", "root", "-p", ROOT_PASSWORD, "--createTable").waitFor());
    List<String> tabletServers = c.instanceOperations().getTabletServers();
    assertEquals(2, tabletServers.size());
    String doomed = tabletServers.get(0);
    assertEquals(0, cluster.exec(Admin.class, "stop", doomed).waitFor());
    tabletServers = c.instanceOperations().getTabletServers();
    assertEquals(1, tabletServers.size());
    assertFalse(tabletServers.get(0).equals(doomed));
  }

}
