package codegen

import (
	"math/rand/v2"
	"sync/atomic"
	"time"
)

const (
	alphabet   = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
	codeSpace  = 36 * 36 * 36 * 36 * 36 * 36
	suffixSize = 6
)

// The random start separates process instances. The counter avoids duplicates
// within a process until it wraps. The database enforces uniqueness across processes.
var (
	start    = rand.Uint64N(codeSpace)
	sequence atomic.Uint64
)

func NewOrderCode(now time.Time) string {
	return "ORD-" + code(now)
}

func NewTrackingCode(now time.Time) string {
	return "TRK-" + code(now)
}

func code(now time.Time) string {
	number := (start + sequence.Add(1)) % codeSpace
	var suffix [suffixSize]byte
	for i := len(suffix) - 1; i >= 0; i-- {
		suffix[i] = alphabet[number%uint64(len(alphabet))]
		number /= uint64(len(alphabet))
	}
	return now.Format("20060102") + "-" + string(suffix[:])
}
