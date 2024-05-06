package taskmaster

import fabric.define.DefType
import fabric.rw.RW

import java.time.Instant

package object task {
  implicit val instantRW: RW[Instant] = RW.from[Instant](
    r = _.toEpochMilli,
    w = json => Instant.ofEpochMilli(json.asLong),
    d = DefType.Int
  )
}