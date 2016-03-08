**NOTE** - this project has now moved to https://github.com/google/thread-weaver


Thread Weaver is a framework for writing multi-threaded unit tests in Java.

It provides mechanisms for creating breakpoints within your code, and for halting execution of a thread when a breakpoint is reached. Other threads can then run while the first thread is blocked. This allows you to write repeatable tests for that can check for race conditions and thread safety.

See the "Wiki" tab for a Users' Guide. For full documentation, please see the "docs" directory on the source tree, or in the main zip file on the Downloads page. The source tree also contains several examples of common race conditions, and shows how to test these using Thread Weaver.