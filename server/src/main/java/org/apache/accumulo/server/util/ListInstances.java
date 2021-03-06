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
package org.apache.accumulo.server.util;

import java.util.Formattable;
import java.util.Formatter;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.accumulo.core.Constants;
import org.apache.accumulo.core.cli.Help;
import org.apache.accumulo.core.conf.Property;
import org.apache.accumulo.fate.zookeeper.ZooCache;
import org.apache.accumulo.fate.zookeeper.ZooReader;
import org.apache.accumulo.server.conf.ServerConfiguration;
import org.apache.accumulo.server.zookeeper.ZooLock;
import org.apache.log4j.Logger;

import com.beust.jcommander.Parameter;

public class ListInstances {
  
  private static final Logger log = Logger.getLogger(ListInstances.class);
  
  private static final int NAME_WIDTH = 20;
  private static final int UUID_WIDTH = 37;
  private static final int MASTER_WIDTH = 30;
  
  private static final int ZOOKEEPER_TIMER_MILLIS = 30 * 1000;
  
  static class Opts extends Help {
    @Parameter(names="--print-errors", description="display errors while listing instances")
    boolean printErrors = false;
    @Parameter(names="--print-all", description="print information for all instances, not just those with names")
    boolean printAll = false;
    @Parameter(names={"-z", "--zookeepers"}, description="the zookeepers to contact")
    String keepers = null;
  }
  static Opts opts = new Opts();
  static int errors = 0;
  
  public static void main(String[] args) {
    opts.parseArgs(ListInstances.class.getName(), args);
    
    if (opts.keepers == null) {
      opts.keepers = ServerConfiguration.getSiteConfiguration().get(Property.INSTANCE_ZK_HOST);
    }
    
    System.out.println("INFO : Using ZooKeepers " + opts.keepers);
    ZooReader rdr = new ZooReader(opts.keepers, ZOOKEEPER_TIMER_MILLIS); 
    ZooCache cache = new ZooCache(opts.keepers, ZOOKEEPER_TIMER_MILLIS);

    TreeMap<String,UUID> instanceNames = getInstanceNames(rdr);
    
    System.out.println();
    printHeader();
    
    for (Entry<String,UUID> entry : instanceNames.entrySet()) {
      printInstanceInfo(cache, entry.getKey(), entry.getValue());
    }
    
    TreeSet<UUID> instancedIds = getInstanceIDs(rdr);
    instancedIds.removeAll(instanceNames.values());
    
    if (opts.printAll) {
      for (UUID uuid : instancedIds) {
        printInstanceInfo(cache, null, uuid);
      }
    } else if (instancedIds.size() > 0) {
      System.out.println();
      System.out.println("INFO : " + instancedIds.size() + " unamed instances were not printed, run with --print-all to see all instances");
    } else {
      System.out.println();
    }
    
    if (!opts.printErrors && errors > 0) {
      System.err.println("WARN : There were " + errors + " errors, run with --print-errors to see more info");
    }
    
  }
  
  private static class CharFiller implements Formattable {
    
    char c;
    
    CharFiller(char c) {
      this.c = c;
    }
    
    @Override
    public void formatTo(Formatter formatter, int flags, int width, int precision) {
      
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < width; i++)
        sb.append(c);
      formatter.format(sb.toString());
    }
    
  }
  
  private static void printHeader() {
    System.out.printf(" %-" + NAME_WIDTH + "s| %-" + UUID_WIDTH + "s| %-" + MASTER_WIDTH + "s%n", "Instance Name", "Instance ID", "Master");
    System.out.printf("%" + (NAME_WIDTH + 1) + "s+%" + (UUID_WIDTH + 1) + "s+%" + (MASTER_WIDTH + 1) + "s%n", new CharFiller('-'), new CharFiller('-'),
        new CharFiller('-'));
    
  }
  
  private static void printInstanceInfo(ZooCache cache, String instanceName, UUID iid) {
    String master = getMaster(cache, iid);
    if (instanceName == null) {
      instanceName = "";
    }
    
    if (master == null) {
      master = "";
    }
    
    System.out.printf("%" + NAME_WIDTH + "s |%" + UUID_WIDTH + "s |%" + MASTER_WIDTH + "s%n", "\"" + instanceName + "\"", iid, master);
  }
  private static String getMaster(ZooCache cache, UUID iid) {
    
    if (iid == null) {
      return null;
    }
    
    try {
      String masterLocPath = Constants.ZROOT + "/" + iid + Constants.ZMASTER_LOCK;
      byte[] master = ZooLock.getLockData(cache, masterLocPath, null);
      if (master == null) {
        return null;
      }
      return new String(master);
    } catch (Exception e) {
      handleException(e);
      return null;
    }
  }
  
  private static TreeMap<String,UUID> getInstanceNames(ZooReader zk) {
    
    String instancesPath = Constants.ZROOT + Constants.ZINSTANCES;
    
    TreeMap<String,UUID> tm = new TreeMap<String,UUID>();
    
    List<String> names;
    
    try {
      names = zk.getChildren(instancesPath);
    } catch (Exception e) {
      handleException(e);
      return tm;
    }
    
    for (String name : names) {
      String instanceNamePath = Constants.ZROOT + Constants.ZINSTANCES + "/" + name;
      try {
        UUID iid = UUID.fromString(new String(zk.getData(instanceNamePath, null)));
        tm.put(name, iid);
      } catch (Exception e) {
        handleException(e);
        tm.put(name, null);
      }
    }
    
    return tm;
  }
  
  private static TreeSet<UUID> getInstanceIDs(ZooReader zk) {
    TreeSet<UUID> ts = new TreeSet<UUID>();
    
    try {
      List<String> children = zk.getChildren(Constants.ZROOT);
      
      for (String iid : children) {
        if (iid.equals("instances"))
          continue;
        try {
          ts.add(UUID.fromString(iid));
        } catch (Exception e) {
          log.error("Exception: " + e);
        }
      }
    } catch (Exception e) {
      handleException(e);
    }
    
    return ts;
  }
  
  private static void handleException(Exception e) {
    if (opts.printErrors) {
      e.printStackTrace();
    }
    
    errors++;
  }
}
