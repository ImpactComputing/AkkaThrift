package driver

import java.net.InetSocketAddress

import akka.actor._

import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.TProcessor

import org.apache.thrift.transport.{TServerSocket,TNonblockingServerSocket}
import org.apache.thrift.server.{TThreadedSelectorServer, TSimpleServer}

import com.impact.akkathrift._
import test.thrift._

class TestImpl extends Test.Iface {
  def hello(name:String): String = s"Hello $name!"
} 

object Driver {
  def threadedServer(port:Int) {
    val socket = new TNonblockingServerSocket(port)
    val processor = new Test.Processor(new TestImpl())
    val server = new TThreadedSelectorServer(
      new TThreadedSelectorServer.Args(socket).processor(processor))
    
    server.serve()

  }
  def akkaServer(port:Int) {
    implicit val actorSystem = AkkaThriftServer.actorSystem

    val address = new InetSocketAddress(port)
    val atss = new AkkaThriftServerSocket(address)
    val proc = new Test.Processor(new TestImpl())
    val server = new AkkaThriftServer(
      new AkkaThriftServer.Args(atss).processor(proc)
    )
    server.serve()

  }
  def main(args: Array[String]) {
    assert(args.nonEmpty)
    akkaServer(args.head.toInt)
  }
}
