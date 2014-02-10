package io.mesosphere.mesosaurus

import org.apache.mesos.{ Scheduler, SchedulerDriver }
import org.apache.mesos.Protos._
import scala.collection.JavaConversions._

/**
  * Mesos scheduler for the Mesosaurus framework.
  *
  * Delegates to a task source to generate (task info) descriptions of tasks to run.
  */
class MesosaurusScheduler(private val _taskSource: TaskSource)
    extends Scheduler with Logging {

  /**
    * Invoked when the scheduler successfully registers with a Mesos
    * master. A unique ID (generated by the master) used for
    * distinguishing this framework from others and MasterInfo
    * with the ip and port of the current master are provided as arguments.
    */
  def registered(
    driver: SchedulerDriver,
    frameworkId: FrameworkID,
    masterInfo: MasterInfo): Unit = {
    log.info("Scheduler.registered")
  }

  /**
    * Invoked when the scheduler re-registers with a newly elected Mesos master.
    * This is only called when the scheduler has previously been registered.
    * MasterInfo containing the updated information about the elected master
    * is provided as an argument.
    */
  def reregistered(
    driver: SchedulerDriver,
    masterInfo: MasterInfo): Unit = {
    log.info("Scheduler.reregistered")
  }

  private val _filters = Filters.newBuilder().setRefuseSeconds(1).build()

  /**
    * Invoked when resources have been offered to this framework. A
    * single offer will only contain resources from a single slave.
    * Resources associated with an offer will not be re-offered to
    * _this_ framework until either (a) this framework has rejected
    * those resources or (b)
    * those resources have been rescinded.
    * Note that resources may be concurrently offered to more than one
    * framework at a time (depending on the allocator being used). In
    * that case, the first framework to launch tasks using those
    * resources will be able to use them while the other frameworks
    * will have those resources rescinded (or if a framework has
    * already launched tasks with those resources then those tasks will
    * fail with a TASK_LOST status and a message saying as much).
    */
  def resourceOffers(driver: SchedulerDriver, offers: java.util.List[Offer]): Unit = {
    if (_taskSource.doneCreatingTasks()) {
      for (offer <- offers) {
        driver.declineOffer(offer.getId());
      }
    }
    else {
      log.info("Scheduler.resourceOffers")
      for (offer <- offers) {
        val taskInfos = _taskSource.generateTaskInfos(offer);
        driver.launchTasks(offer.getId(), taskInfos, _filters);
      }
    }
  }

  /**
    * Invoked when an offer is no longer valid (e.g., the slave was
    * lost or another framework used resources in the offer). If for
    * whatever reason an offer is never rescinded (e.g., dropped
    * message, failing over framework, etc.), a framwork that attempts
    * to launch tasks using an invalid offer will receive TASK_LOST
    * status updats for those tasks.
    */
  def offerRescinded(
    driver: SchedulerDriver,
    offerId: OfferID): Unit = {
    log.info("Scheduler.offerRescinded")
  }

  private var _nTasksTerminated = 0;

  /**
    * Invoked when the status of a task has changed (e.g., a slave is
    * lost and so the task is lost, a task finishes and an executor
    * sends a status update saying so, etc). Note that returning from
    * this callback _acknowledges_ receipt of this status update! If
    * for whatever reason the scheduler aborts during this callback (or
    * the process exits) another status update will be delivered (note,
    * however, that this is currently not true if the slave sending the
    * status update is lost/fails during that time).
    */
  def statusUpdate(driver: SchedulerDriver, taskStatus: TaskStatus): Unit = {
    log.info("Scheduler.statusUpdate")
    _taskSource.observeTaskStatusUpdate(taskStatus);
    if (_taskSource.done()) {
      driver.stop();
    }
  }

  /**
    * Invoked when an executor sends a message. These messages are best
    * effort; do not expect a framework message to be retransmitted in
    * any reliable fashion.
    */
  def frameworkMessage(
    driver: SchedulerDriver,
    executorId: ExecutorID,
    slaveId: SlaveID,
    data: Array[Byte]): Unit = {
    log.info("Scheduler.frameworkMessage")
  }

  /**
    * Invoked when the scheduler becomes "disconnected" from the master
    * (e.g., the master fails and another is taking over).
    */
  def disconnected(driver: SchedulerDriver): Unit = {
    ???
  }

  /**
    * Invoked when a slave has been determined unreachable (e.g.,
    * machine failure, network partition). Most frameworks will need to
    * reschedule any tasks launched on this slave on a new slave.
    */
  def slaveLost(
    driver: SchedulerDriver,
    slaveId: SlaveID): Unit = {
    log.info("Scheduler.slaveLost")
  }

  /**
    * Invoked when an executor has exited/terminated. Note that any
    * tasks running will have TASK_LOST status updates automagically
    * generated.
    */
  def executorLost(
    driver: SchedulerDriver,
    executorId: ExecutorID,
    slaveId: SlaveID,
    status: Int): Unit = {
    log.info("Scheduler.executorLost")
  }

  /**
    * Invoked when there is an unrecoverable error in the scheduler or
    * scheduler driver. The driver will be aborted BEFORE invoking this
    * callback.
    */
  def error(driver: SchedulerDriver, message: String): Unit = {
    log.info("Scheduler.error")
  }

}