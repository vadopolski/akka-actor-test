import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JobManager private (context: ActorContext[JobManager.JobManagerCommand]) {
  import JobManager._

  def behavior(state: Int): Behavior[JobManagerCommand] =
    Behaviors.receiveMessage {
      case Register(who, taskManager) =>
        who ! RegistrationAck
        online(taskManager, state)
    }

  def online(taskActorRef: ActorRef[TaskManagerCommand], totalWordCount: Int): Behavior[JobManagerCommand] =
    Behaviors.receiveMessage {
      case Work(text) =>
        taskActorRef ! TaskWork(text)
        online(taskActorRef, 0)
      case WorkCompleted(count, originalRequester) =>
        val newTotalWordCount = someLogic(totalWordCount, count)
        originalRequester ! Report(newTotalWordCount)
        behavior(0)
    }
}

object JobManager {
  sealed trait JobManagerCommand
  case class Register(who: ActorRef[ClientReply], taskManager: ActorRef[TaskManagerCommand]) extends JobManagerCommand
  case class Work(text: String)                                                              extends JobManagerCommand
  case class WorkCompleted(count: Int, originalRequester: ActorRef[ClientReply])             extends JobManagerCommand

  sealed trait TaskManagerCommand
  case class TaskWork(text: String) extends TaskManagerCommand

  sealed trait ClientReply
  object RegistrationAck             extends ClientReply
  case class Report(totalCount: Int) extends ClientReply

  def someLogic(totalWordCount: Int, count: Int): Int = totalWordCount + count

  def apply(): Behavior[JobManagerCommand] =
    Behaviors.setup { context =>
      new JobManager(context).behavior(0)
    }
}

class FlinkCountTypedSpec extends AnyWordSpec with Matchers {
  import JobManager._

  "A method manager actor" should {
    "return correct int value" in {
      val totalWordCount = 5
      val count          = 1
      val expectedResult = 6

      val actualResult = JobManager.someLogic(totalWordCount, count)

      actualResult shouldBe expectedResult
    }
  }


  "A Job manager actor" should {
    "register Task manager actor" in {
      val kit = ActorTestKit()
      val jobManagerActor  = kit.spawn(JobManager())

      val taskManagerProbe = kit.createTestProbe[TaskManagerCommand]()
      val clientProbe      = kit.createTestProbe[ClientReply]()

      jobManagerActor ! Register(clientProbe.ref, taskManagerProbe.ref)

      clientProbe.expectMessage(RegistrationAck)
    }

    "send the work to the Task Manager actor" in {
      val kit = ActorTestKit()
      val jobManagerActor  = kit.spawn(JobManager())
      val taskManagerProbe = kit.createTestProbe[TaskManagerCommand]()
      val clientProbe      = kit.createTestProbe[ClientReply]()

      jobManagerActor ! Register(clientProbe.ref, taskManagerProbe.ref)

      clientProbe.expectMessage(RegistrationAck)

      val wordCountText = "I love summer"
      jobManagerActor ! Work(wordCountText)

      taskManagerProbe.expectMessage(TaskWork(wordCountText))

      taskManagerProbe.expectMessage()
    }
  }
}
