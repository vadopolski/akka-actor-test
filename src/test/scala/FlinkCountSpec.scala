
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}


class FlinkCountSpec extends TestKit(ActorSystem("TestProbeSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TestProbeSpec._

  "A master actor" should {
    "register a slave" in {
      val jobActor = system.actorOf(Props[JobActor])
      val taskActor = TestProbe("taskActor")

      jobActor ! Register(taskActor.ref)
      expectMsg(RegistrationAck)
    }

    "send the work to the slave actor" in {
      val jobActor = system.actorOf(Props[JobActor])
      val taskActor = TestProbe("taskActor")
      jobActor ! Register(taskActor.ref)
      expectMsg(RegistrationAck)

      val workloadString = "I love Akka"
      jobActor ! Work(workloadString)

      // the interaction between the jobActor and the taskActor actor
      taskActor.expectMsg(TaskWork(workloadString, testActor))
      taskActor.reply(WorkCompleted(3, testActor))

      expectMsg(Report(3)) // testActor receives the Report(3)
    }

    "aggregate data correctly" in {
      val master = system.actorOf(Props[JobActor])
      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workloadString = "I love Akka"
      master ! Work(workloadString)
      master ! Work(workloadString)

      // in the meantime I don't have a slave actor
      slave.receiveWhile() {
        case TaskWork(`workloadString`, `testActor`) => slave.reply(WorkCompleted(3, testActor))
      }

      expectMsg(Report(3))
      expectMsg(Report(6))
    }
  }
}

object TestProbeSpec {
  // scenario
  /*
    word counting actor hierarchy master-slave

    send some work to the master
      - master sends the slave the piece of work
      - slave processes the work and replies to master
      - master aggregates the result
    master sends the total count to the original requester
   */

  case class Work(text: String)
  case class TaskWork(text: String, originalRequester: ActorRef)
  case class WorkCompleted(count: Int, originalRequester: ActorRef)
  case class Register(slaveRef: ActorRef)
  case object RegistrationAck
  case class Report(totalCount: Int)

  class JobActor extends Actor {
    override def receive: Receive = {
      case Register(taskActorRef) =>
        sender() ! RegistrationAck
        println("!!!!!!!!!!!!!!!!!!!!!")
        context.become(online(taskActorRef, 0))
      case _ => // ignore
    }

    def online(taskActorRef: ActorRef, totalWordCount: Int): Receive = {
      case Work(text) => taskActorRef ! TaskWork(text, sender())
      case WorkCompleted(count, originalRequester) =>
        val newTotalWordCount = totalWordCount + count
        originalRequester ! Report(newTotalWordCount)
        context.become(online(taskActorRef, newTotalWordCount))
      case _ => println("Nothing")
    }
  }

  // class Slave extends Actor ....
}
