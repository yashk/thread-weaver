/*
 * Copyright 2009 Weaver authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.testing.threadtester;

import java.lang.reflect.Method;

/**
 * Implementation of {@link MainRunnable} with empty methods. Provided as a
 * convenience class, so that subclasses only need to implement the methods that
 * they need.
 *
 * @param <T> the type under test. It is expected that the runnable will invoke a
 * method on this class in its {@link ThrowingRunnable#run} method.
 *
 * @author alasdair.mackintosh@gmail.com (Alasdair Mackintosh)
 */
public abstract class MainRunnableImpl<T> implements MainRunnable<T> {

  @Override
  public Class<T> getClassUnderTest() {
    return null;
  }

  @Override
  public String getMethodName() {
    return null;
  }

  @SuppressWarnings("unused")
  @Override
  public Method getMethod() throws NoSuchMethodException {
    return null;
  }

  @Override
  public void initialize() throws Exception {
    // do nothing
  }

  @Override
  public T getMainObject() {
    return null;
  }

  @Override
  public void terminate() throws Exception {
    // do nothing
  }
}
