/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package it.marcoreni.nexus3.shutdownbackup;


import java.util.concurrent.TimeUnit;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.common.app.ManagedLifecycle;
import org.sonatype.nexus.common.event.EventAware;
import org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport;
import org.sonatype.nexus.scheduling.TaskInfo;
import org.sonatype.nexus.scheduling.TaskRemovedException;
import org.sonatype.nexus.scheduling.TaskScheduler;
import org.sonatype.nexus.internal.backup.DatabaseBackupTaskDescriptor;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sonatype.nexus.common.app.ManagedLifecycle.Phase.TASKS;

/**
 * Triggers a backup on shutdown
 */
@Named
@ManagedLifecycle(phase = TASKS)
@Priority(Integer.MIN_VALUE)
@Singleton
public class ShutdownBackupHandler
    extends StateGuardLifecycleSupport
    implements EventAware {

  private final TaskScheduler taskScheduler;

  private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownBackupHandler.class);

  @Inject
  public ShutdownBackupHandler(final TaskScheduler taskScheduler) {
    this.taskScheduler = checkNotNull(taskScheduler);
  }

  @Override
  protected void doStop() {
    LOGGER.debug("Before stopping, let's backup the DB...");
    for (TaskInfo task : taskScheduler.listsTasks()) {
      if (DatabaseBackupTaskDescriptor.TYPE_ID
          .equals(task.getConfiguration().getTypeId())) {
        try {
          task.runNow();
        } catch (TaskRemovedException|IllegalStateException e) {
          LOGGER.error("Error while triggering task run", e);
        }
        LOGGER.debug("Now we will wait; future is " + task.getCurrentState().getFuture() + "...");
        while (task.getCurrentState().getFuture() != null && !task.getCurrentState().getFuture().isDone()) {
          try {
            LOGGER.debug("Going to sleep since state is " + task.getCurrentState().getFuture().isDone() + "...");
            TimeUnit.SECONDS.sleep(1);
            LOGGER.debug("Waking up...");
          } catch (Exception e) {
            LOGGER.error("Error while waiting for task to be over", e);
          }
        }
        LOGGER.debug("All done...");
        break;
      }
    }
  }
}
