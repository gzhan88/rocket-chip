// See LICENSE.SiFive for license details.

package freechips.rocketchip.util

import Chisel._

/** Implements the same interface as chisel3.util.Queue, but uses a shift
  * register internally.  It is less energy efficient whenever the queue
  * has more than one entry populated, but is faster on the dequeue side.
  * It is efficient for usually-empty flow-through queues. */
class ShiftQueue[T <: Data](gen: T,
                            val entries: Int,
                            pipe: Boolean = false,
                            flow: Boolean = false)
    extends Module {
  val io = IO(new QueueIO(gen, entries) {
    val mask = UInt(OUTPUT, entries)
  })

  private val valid = RegInit(Vec.fill(entries) { Bool(false) })
  private val elts = Reg(Vec(entries, gen))

  private val do_enq = io.enq.fire()
  private val do_deq = io.deq.fire()

  for (i <- 0 until entries) {
    val wdata = if (i == entries-1) io.enq.bits else Mux(valid(i+1), elts(i+1), io.enq.bits)
    val shiftDown = if (i == entries-1) false.B else io.deq.ready && valid(i+1)
    val enqNew = io.enq.fire() && Mux(io.deq.ready, valid(i), !valid(i) && (if (i == 0) true.B else valid(i-1)))
    when (shiftDown || enqNew) { elts(i) := wdata }
  }

  val padded = Seq(true.B) ++ valid ++ Seq(false.B)
  for (i <- 0 until entries) {
    when ( do_enq && !do_deq &&  padded(i+0)) { valid(i) := true.B }
    when (!do_enq &&  do_deq && !padded(i+2)) { valid(i) := false.B }
  }

  io.enq.ready := !valid(entries-1)
  io.deq.valid := valid(0)
  io.deq.bits := elts.head

  if (flow) {
    when (io.enq.valid) { io.deq.valid := true.B }
    when (!valid(0)) { io.deq.bits := io.enq.bits }
  }

  if (pipe) {
    when (io.deq.ready) { io.enq.ready := true.B }
  }

  io.mask := valid.asUInt
  io.count := PopCount(io.mask)
}
