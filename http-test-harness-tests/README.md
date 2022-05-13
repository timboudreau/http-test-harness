HTTP Test Harness Tests
=======================

Tests for the adjacent HTTP test harness project.  They are in a separate project
because they use [Acteur](https://github.com/timboudreau/acteur) to define a web-app, 
and if this were done in the http-test-harness project itself,
that would prevent Acteur itself from using the test harness for its own tests, since it
would create a circular dependency.

Main sources are the web app used for testing, which implements all sorts of perversities we
want to ensure the test harness is robust to.

Test sources are the tests of the http-test-harness project _using_ that application.

For a description of all the HTTP resources the test application serves, start it simply
by building and exec'ing the project, and visit
http://localhost:4949/help?html=true
