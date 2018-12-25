/*
 * Copyright (C) 2013, 2014 Brett Wooldridge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zaxxer.hikari.util;

import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.concurrent.Semaphore;

/**
 * This class implements a lock that can be used to suspend and resume the pool.  It
 * also provides a faux(fake) implementation that is used when the feature is disabled that
 * hopefully gets fully "optimized away" by the JIT.
 *
 * 实现了一虚一实两个java.util.concurrent.Semaphore
 * 由于Hikari的isAllowPoolSuspension默认值是false的，FAUX_LOCK只是一个空方法，acquisitionSemaphore对象也是空的；
 *
 * 如果isAllowPoolSuspension值调整为true，
 * 当收到MBean的suspend调用时将会一次性acquisitionSemaphore.acquireUninterruptibly从此信号量获取给定数目MAX_PERMITS 10000的许可，
 * 在提供这些许可前一直将线程阻塞。
 * 之后HikariPool的getConnection方法获取不到连接，阻塞在suspendResumeLock.acquire()，
 * 除非resume方法释放给定数目MAX_PERMITS 10000的许可，将其返回到信号量。
 *
 * @author Brett Wooldridge
 */
public class SuspendResumeLock
{
   public static final SuspendResumeLock FAUX_LOCK = new SuspendResumeLock(false) {
      @Override
      public void acquire() {}

      @Override
      public void release() {}

      @Override
      public void suspend() {}

      @Override
      public void resume() {}
   };

   private static final int MAX_PERMITS = 10000;
   private final Semaphore acquisitionSemaphore;

   /**
    * Default constructor
    */
   public SuspendResumeLock()
   {
      this(true);
   }

   private SuspendResumeLock(final boolean createSemaphore)
   {
      acquisitionSemaphore = (createSemaphore ? new Semaphore(MAX_PERMITS, true) : null);
   }

   public void acquire() throws SQLException
   {
      if (acquisitionSemaphore.tryAcquire()) {
         return;
      }
      else if (Boolean.getBoolean("com.zaxxer.hikari.throwIfSuspended")) {
         throw new SQLTransientException("The pool is currently suspended and configured to throw exceptions upon acquisition");
      }

      acquisitionSemaphore.acquireUninterruptibly();
   }

   public void release()
   {
      acquisitionSemaphore.release();
   }

   public void suspend()
   {
      acquisitionSemaphore.acquireUninterruptibly(MAX_PERMITS);
   }

   public void resume()
   {
      acquisitionSemaphore.release(MAX_PERMITS);
   }
}
