import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect._
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, BehaviorTestKit, TestInbox}
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import org.slf4j.event.Level
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Random


sealed trait Command
case class JobSubmit(who: ActorRef[String]) extends Command
case class CheckStatus(who: ActorRef[String]) extends Command
case class StopClusterWork(who: ActorRef[String]) extends Command



object Child {
  def apply(): Behavior[Command] = waiting(0)

  def waiting(msgCount: Int): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.receiveMessage {
      case JobSubmit(who) =>
        ctx.log.info("Child recieved")
        who ! "Job submitted"
        inProgress(msgCount + 1)
    }
  }

  def inProgress(msgCount: Int): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.receiveMessage {
      case CheckStatus(who) =>
        who ! "Job in progress"
        Behaviors.same
      case StopClusterWork(who) =>
        who ! "All Jobs correctly finished"
        who ! "Cluster was shutdown"
        waiting(msgCount + 1)
    }
    }

}



object Client {
  val random = new Random()


  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    val child = ctx.spawn(Child(), "child")

    Behaviors.receiveMessage {
      case msg @ JobSubmit(who) =>
        ctx.log.info("Parent recieved")
        child ! msg
        Behaviors.same
      case CheckStatus(who) =>
        if (random.nextBoolean()) who ! "Job in progress" else who ! "Job failed"
        Behaviors.same
      case StopClusterWork(who) =>
        who ! "All Jobs correctly finished"
        who ! "Cluster was shutdown"
        Behaviors.same
    }
  }
  //#under-test

}


class ClientSpec extends AnyWordSpec with Matchers {

  "Typed probe actor" must {

    "send back the message - Job submitted with Synchronous testing" in {
      val testKit = BehaviorTestKit(Client())
      val inbox = TestInbox[String]()

      testKit.run(JobSubmit(inbox.ref))

      val expectedResult = "Job submitted"

//      inbox.expectMessage(expectedResult)
    }

    "send back the message - Job submitted with Asynchronous testing" in {
      val kit = ActorTestKit()
      val client: ActorRef[Command] = kit.spawn(Client())

      val probe = kit.createTestProbe[String]()

      client ! JobSubmit(probe.ref)

      probe.expectMessage("Job submitted")

      client ! CheckStatus(probe.ref)

      probe.expectMessageType[String]
    }


    "send back the message with test probe - any status message" in {
      val kit = ActorTestKit()

      val client = kit.spawn(Client())
      val probe = kit.createTestProbe[String]()

      client ! JobSubmit(probe.ref)

//      probe.expectMessage("Job submitted")

//      probe.expectMessageType[String]
      probe.receiveMessages(1)
    }

  }


}

