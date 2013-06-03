package driver

import java.net.InetSocketAddress

import akka.actor._

import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.TProcessor

import com.impact.akkathrift._
import test.thrift._

class TestImpl extends Test.Iface {
  def hello(name:String): String = s"Hello $name!"
} 

object Driver {
  def main(args: Array[String]) {
    assert(args.nonEmpty)
    val address = new InetSocketAddress(args.head.toInt)
    implicit val actorSystem = AkkaThriftServer.actorSystem

    val atss = new AkkaThriftServerSocket(address)
    val proc = new Test.Processor(new TestImpl())
    val server = new AkkaThriftServer(
      new AkkaThriftServer.Args(atss).processor(proc)
    )
    server.serve()
  }
}
