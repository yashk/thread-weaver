## Introduction ##

This guide runs through some sample test cases using Thread Weaver, and demonstrates
how certain types of test can be written. For a more detailed step-by-step set
of examples, please see the tutorial the the docs directory of the download.

## A Simple Example ##

Suppose we want to test the following class:
```
1: public class MyList extends ArrayList {
2:   public boolean putIfAbsent(Object o) {
3:     boolean absent = !super.contains(o);
4:     if (absent) {
5:       super.add(o):
6:     }
8:   return absent;
9:   }
```
We can write a simple unit test:
```
  public void testPutIfAbsent() {
    MyList list = new MyList();
    list.putIfAbsent("A");
    list.putIfAbsent("A");
    assertEquals(1, list.size());
  }
```

This test will pass, because the second call to putIfAbsent() will not add the
same element to the list. However, the MyList class as designed contains a
threading bug. Suppose two threads call putIfAbsent() simultaneously with the
same value? Both threads could evaluate the 'absent' variable to false, and end
up adding the same object twice. Although the bug exists is difficult to write
a unit test that will demonstrate this behaviour.



## A Simple Thread Weaver Test ##

Thread Weaver allows us to interleave the execution of two separate threads. A simple example would be:
```
  public class MyListTest {
    MyList list;

    @ThreadedBefore
    public void before() {
      list = new MyList();
    }

    @ThreadedMain
    public void mainThread() {
      list.putIfAbsent("A");
    }

    @ThreadedSecondary
    public void secondThread() {
      list.putIfAbsent("A");
    }

    @ThreadedAfter
    public void after() {
      assertEquals(1, list.size());
    }
  }
```

This test class uses a series of annotations to tell Thread Weaver how to run the
tests. Before running any tests, the method annotated with @ThreadedBefore will
be invoked. Then the methods labelled @ThreadedMain and @ThreadedSecondary will
be invoked in two separate threads. Finally, the method annotated with
@ThreadedAfter will be invoked.

The two calls to to putIfAbsent will be interleaved. The first thread (which
invokes the @ThreadedMain method) will stop on the first line of
putIfAbsent. The second thread (which invokes the @ThreadedSecondary method)
will then run to completion, after which the first thread will finish. When
both threads are finished, the @ThreadedAfter method will be invoked. This
process is then repeated, breaking on the second executable line of
putIfAbsent, and so forth.

In order to invoke Thread Weaver, you must add a special invocation to your test
class. Using JUnit 4 syntax, this would be:

```
  public class MyListTest {
    // This method is invoked by the regular JUnit test runner.
    @Test
    public void testThreading() {
      AnnotatedTestRunner runner = new AnnotatedTestRunner();
      // Run all Weaver tests in this class, using MyList as the Class Under Test.
      runner.runTests(this.getClass(), MyList.class);
    }
    ...
  }
```

The runTests() method tells the runner to execute all of the threaded tests
defined in MyListTest. Passing in MyList.class as the second argument tells
Weaver which class is being tested. Weaver will break the main test thread at
the first method in MyList that gets invoked. (In this case, it will break at
putIfAbsent()).


#### Exceptions and Test Failures ####

If any of the methods invoked by the test runner throw an exception, that
exception will be wrapped in a RuntimeException, and thrown by the runTests()
method. Thus any exceptions encountered in the test will result in a test
failure. Note that the various annotated methods invoked by the runner may be
declared to throw checked exceptions.


```
  @ThreadedMain
  public void openFile() throws IOException {
     ...
```
Any IOException thrown will be wrapped in a RuntimeException and thrown from runTests().


## Controlling how Threads Break ##

If you need finer-grained control over exactly where the test threads stop, Thread Weaver provides facilities that will let you do this.



### Code Positions and Breakpoints ###

Weaver allows you to specify positions within the source code of the classes
being tested. Using these positions, you can then create Breakpoints, which
will cause threads to stop executing when they hit them. Using these
Breakpoints, you can control exactly how your multithreaded tests behave. In
addition, Weaver offer various utilility classes for defining and executing
test threads.

Internally, Weaver handles these breakpoints by using byte-code instrumentation. The classes under test are loaded by a custom classloader which adds additional callbacks into the byte code. These callbacks are used to stop and resume executing threads. (Note that it is also possible to use Weaver without using instrumentation, provided that you can inject fake or mock objects into your test class. See the "!Without Instrumentation" section below.



### CodePositions ###

A CodePosition represents a location within an instrumented class. (Weaver can
only define positions inside classes that it has instruemnted.) The code
fragment below creates a CodePosition that represents the point immediately
after the call to super.contains() in putIfAbsent(). The approach is analagous
to creating an[http://www.easymock.org/Documentation.html[EasyMock](EasyMock.md)]
object and using the expect() syntax.

```
  MyList list = new MyList();
  MethodRecorder<MyList> recorder = new MethodRecorder<MyList>(list);
  MyList control = recorder.getControl();
  Collection target = recorder.createTarget(Collection.class);
  CodePosition position = recorder.inMethod(control.putIfAbsent(null))
    .afterCalling(target.contains(null))
```
Let's break this down line by line.

Create a new MyList instance, and then create a MethodRecorder to record the methods that we invoke.

```
  MyList list = new MyList();
  MethodRecorder<MyList> recorder = new MethodRecorder<MyList>(list);
```

Ask the MethodRecorder to create a new control object. This is a dummy instance
of MyList. We can record the methods that we invoke upon it, in order to
specify code positions.

```
  MyList control = recorder.getControl();
```

Ask the MethodRecorder to create a new target object. This is a dummy instance
of Collection that we use to record method calls.

```
  Collection target = recorder.createTarget(Collection.class);
```

Finally, create a CodePosition that represents the the point just after calling
super.contains() in putIfAbsent().

```
  CodePosition position = recorder.inMethod(control.putIfAbsent(null))
    .afterCalling(target.contains(null))
```

As well as using the MethodRecorder, you can also specify CodePositions explicitly using Method objects or strings.

```
  MyList list = new MyList();
  ClassInstrumentation clss = Instrumentation.getClassInstrumentation(MyList.class);
  // Create a CodePosition that represents a call to
  // the method named "contains" within the method "putIfAbsent"
  Method putMethd = MyList.class.getDeclaredMethod("putIfAbsent", Object.class);
  Method containsMethod = Collection.class.getDeclaredMethod("contains", Object.class);
  CodePosition position = clss.afterCall(putMethod, containsMethod);
```
or
```
  CodePosition position = clss.afterCall("putIfAbsent", "contains");
```


### Using CodePositions ###

You can use a CodePosition to control how Weaver interleaves two threads of
execution. Recall that in our initial example of the MyListTest class, Weaver
would break at every line in the putIfAbsent method in order to allow the
second thread to execute. Suppose that we want the initial thread to execute
until it returns from the call to super.contains(), and only then do we want
the second thread to start.

```
1: public class MyList extends ArrayList {
2:   public boolean putIfAbsent(Object o) {
3:     boolean absent = !super.contains(o);  <-- Stop here, let second thread run
```
We can add an option to our test class to specify that Weaver should stop the first thread at this position.
```
  public class MyListTest {
    MyList list;

   @ThreadedOptions
   public AnnotatedTestOptions getOptions() {
     ...
     CodePosition position = recorder.inMethod(control.putIfAbsent(null))
      .afterCalling(target.contains(null))
     return new AnnotatedTestOptions(position);
   }
```


## Using Scripts to control Threads ##

To gain additional control over how threads are paused and resumed, Weaver
offers the concept of Scripts. A Script represents a series of tasks to be
performed in a single thread. Only one script may be active at a time, and each
script can release control to another script.

Scripts use a similar mechanism to the MethodRecorder class to allow release
points to be specified.

```
  final MyList list = new MyList();

  Script<MyList> main = new Script<MyList>(list);
  Script<MyList> second = new Script<MyList>(list);

  // Create control and target objects to allow us to specify a release point.
  final MyList control = main.object();
  final Collection target = main.createTarget(Collection.class);

  main.addTask(new ScriptedTask<MyList>() {
    @Override
    public void execute() {
      // Release to the second script while calling "putIfAbsent"
      main.in(control.putIfAbsent(null)).afterCalling(target.contains(null).release(second));
      list.putIfAbsent("A");
      // We will return here after the second script releases us
      list.putIfAbsent("B");
      release(second);
    }
  });

  second.addTask(new ScriptedTask<SimpleClass3>() {
    @Override
    public void execute() {
      list.putIfAbsent("A");
      release(main);
      list.putIfAbsent("B");
    }
  });

  new Scripter<MyList>(main, second).execute();
```

As the example above shows, scripts can either release control explicitly (as
the seond script does) or they can declare an intention to release control
during a call to an instrumented method. (This is what the first script is
doing.) This allows you maximum flexibility in determining when one thread
should yield to another.

Although the example shows two scripts being used, it is possible to combine
three or more scripts into a single scripter, and release between them.

## Low Level Thread Control ##

If the mechanisms defined previously are not sufficient, Weaver allows you to
take complete control over the way that your threads execute. You can use an
InterleavedRunner to interleave a specific set of runnable objects, and control
where the interleaving takes place. Or you can create Breakpoints that
determine exactly where a thread will stop, and handle the thread execution
yourself.


### Using InterleavedRunner ###

The InterleavedRunner takes two runnable tasks and interleaves them in separate
threads. When the first task reaches the first executable line of the test
method, it breaks, and the second task is run to completion. The test is then
repeated, breaking onthe second executable line, and so forth. The
InterleavedRunner also handles the case where the second task cannot continue
because of a synchronisation lock held by the first task. In that case, it will
step through the first task until the lock is released, and then run the second
task.

To use an InterleavedRunner you must define the two tasks.

```
  // The main runnable. Creates a new MyList object for each iteration
  private static class MainTest extends MainRunnableImpl<MyList> {
    private MyList list;

    public Class<MyList> getMainClass() {
      // The class being tested
      return MyList.class;
    }

    public String getMethodName() {
      // The method being tested
      return "putIfAbsent";
    }

    public void initialize() {
      list = new MyList();
    }

    public void run() {
      // Invokes the method being tested in the class being tested
      list.putIfAbsent("A");
    }

    public void terminate() {
      assertEquals(1, list.size());
    }
  }

  // The secondary runnable. Uses the MyList object created by the MainTest
  private static class SecondaryTest extends SecondaryRunnableImpl<MyList, MainTest> {
    private MyList list;

    public void initialize(MainTest main) {
      list = main.list;
    }

    public void run() {
      list.putIfAbsent("A");
    }

    public void terminate() {
      assertEquals(1, list.size());
    }
  }

  // The test method that uses the two runnable classes
  @ThreadedTest
  public void testPutIfAbsent() throws Throwable {
    MainTest main = new MainTest();
    SecondaryTest secondary = new SecondaryTest();

    // Use an InterleavedRunner to run the two tests
    InterleavedRunner<MyList> runner = new InterleavedRunner<MyList>();
  }
```

As the tests are running in separate threads, you need to handle any exceptions
that they may throw. You can use the RunResult to get access to these:

```
 RunResult result = runner.interleave(main, secondary);
 // Throws any exceptions produced by the two threads. Can also
 // query for individual exceptions.
 result.throwExceptionsIfAny();
```

You can also control where the main runnable breaks for the first time. By
default, this is the first executable line of the test method, but you can also
specify a CodePosition as the first break point.

```
  // Specify the point where the main runnable first breaks.
  CodePosition position = recorder.inMethod(...
  RunResult result = runner.interleave(main, secondary, position);

```
Or you can specify an explicit list of positions, and the main runnable will only break there:
```
  // Main runnable only breaks at these positions
  CodePosition position1 = recorder.inMethod(...
  CodePosition position2 = recorder.inMethod(...).beforeCall(...)
  List<CodePosition> positions = Lists.newArrayList(position1, position2);
  RunResult result = runner.interleave(main, secondary, positions);
```


### Using Breakpoints ###

For complete control, you can create Breakpoints, and use these to suspend and
resume your test threads explicitly. Here is a sample test using Breakpoints.

```
  @ThreadedTest
  public void putIfAbsent() throws Exception {
    final UniqueList<String> list = new UniqueList<String>();
    ClassInstrumentation clss = Instrumentation.getClassInstrumentation(UniqueList.class);
    /// Create a CodePosition at the point where we want to stop
    Method putMethod = UniqueList.class.getDeclaredMethod("putIfAbsentInternal", Object.class);
    Method containsMethod = ArrayList.class.getDeclaredMethod("contains", Object.class);
    CodePosition position = clss.afterCall(putMethod, containsMethod);

    Runnable task = new Runnable() {
        public void run() { list.putIfAbsent("A"); }
      };
    Thread thread1 = new Thread(task);

    // Create a Breakpoint from the code position and thread.
    ObjectInstrumentation<UniqueList<String>> instrumented =
        Instrumentation.getObjectInstrumentation(list);
    Breakpoint bp = instrumented.createBreakpoint(position, thread1);

    // Start the thread. It will run until it hits the Breakpoint
    thread1.start();

    // Wait until the breakpoint is reached. When we return from
    // await, the first thread will be at 'position'
    bp.await();

    // Update the list in the main thread.
    list.putIfAbsent("A");

    // Let the first thread continue. It will call super.add()
    bp.resume();
    // Wait for the first thread to finish
    thread1.join();

    assertEquals(1, list.size());
  }
```

With the initial (unsynchronized) version of MyList, the assertion at the end of this test will fail. We can fix to MyList by making the putIfAbsent method synchronized.
```
 public synchronized void putIfAbsent(Object o) {
```

However, the test code above now has a problem. If we put a breakpoint in the
middle of putIfAbsent(), and then try to call putIfAbsent() again, then the
second thread will block. To avoid this problem, we can use two separate
threads, and use a ThreadMonitor to manage the execution of the second
thread. (A ThreadMonitor waits for a thread to finish, and lets you know when
it's blocked.) This will return with a status code if the second thread is
blocked.
```
  Thread thread1 = new Thread(task);
  Thread thread2 = new Thread(task);
  thread1.start();
  bp.await();
  thread2.start();
  ThreadMonitor monitor = new ThreadMonitor(thread2, thread1);

  // Wait for the second thread to finish. Returns false if the second
  // thread is blocked, or true if it ran to completion
  boolean secondFinished = monitor.waitForThread();
  bp.carryOn();
  thread1.join();

 // If the second thread did not run to completion, wait for it.
 if (!secondFinished) {
   thread2.join();
 }
 assertEquals(1, list.size());
```

### More on Breakpoints ###

Breakpoints are normally specific to a particular object, in a particular thread. In the example above, we obtained an ObjectInstrumentation for the "list" instance, and created a breakpoint for that instance, in the thread "thread". The breakpoint will only be triggered when the given object is invoked in the given thread.

Breakpoints are normally single-shot. Once a Breakpoint has been reached, it
will no longer be triggered. However, Breakpoints can have an associated count
(so that they will only be triggered the Nth time they are hit) and they can be
temporarily disabled, and later re-enabled.



## Using Breakpoints without Instrumentation ##

If your test uses fake or mock objects, you can use Weaver's built-in
Breakpoint adapters. Suppose we have the following class:

```
  public class UserManager {
    private UserDatabase db;

    public void addUser(String userName) {
      if (!db.contains(userName)) {
        db.add(userName);
      }
    }
  }
```

If you are using EasyMock to create a mock UserDatabase then you can create a
BlockingAnswer (which implements Breakpoint) as follows:

```
  Thread thread1;
  UserDatabase mockDb = createStrictMock(UserDatabase.class);
  BlockingAnswer<Boolean> answer =
      new BlockingAnswer<Boolean>(false, thread1);
  mockDb.contains(USERNAME).andAnswer(answer);
  UserManager manager = new UserManager(mockDb);
  ....
  thread1.start();
  answer.await();
```
When "thread1" invokes mockDb.contains(), it will block.

If you are using a handwritten fake object, you can obtain a similar result using a BlockingProxy.
```
  Thread thread1;
  UserDatabase fakeDb = new FakeDatabase();
  UserDatabase proxy = BlockingProxy.create(UserDatabase.class, fakeDb, "getUser");
  UserManager manager = new UserManager(proxy);

   ....
  thread1.start();
  proxy.await();
```

The BlockingProxy will proxy all calls to the underlying FakeDatabase object,
and will break when it reaches the getUser method.

The above example shows the explicit await()method being used. It is also
possible to combine one of these break points with the InterleavedRunner, to
avoid having to make explicit await()/carryOn() calls.

Note that these two Breakpoint types cannot be used in all circumstances. In
the initial MyList class, for example, there is no injectable test object where
we can create a BlockingProxy or BlockingAnswer. In order to create Breakpoints
here, we need to use instrumentation.


Copyright 2009 Weaver authors
Licensed under the Apache License, Version 2.0 (the "License")