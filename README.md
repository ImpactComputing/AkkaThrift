AkkaThrift
==========

A Scala Akka.IO based Thrift server

AkkaThrift provides a pure Akka based TCP server for handling thrift services.  It provides a good way to use Thrift for external APIs while using Akka internally.

Example Usage:

    import com.impact.akkathrift._

    implicit val actorSystem = AkkaThriftServer.actorSystem

    val address = new InetSocketAddress(port)
    val atss = new AkkaThriftServerSocket(address)
    val proc = new Test.Processor(new TestImpl())
    val server = new AkkaThriftServer(
      new AkkaThriftServer.Args(atss).processor(proc)
    )   
    server.serve()

