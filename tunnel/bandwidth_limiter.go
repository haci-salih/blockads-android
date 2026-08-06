package tunnel

import (
	"context"
	"io"
	"sync"

	"golang.org/x/time/rate"
)

// ─────────────────────────────────────────────────────────────────────────────
// bandwidth_limiter.go — global upload/download throttling (root-less).
//
// This throttles at the single point every byte must pass through in
// full-tunnel mode: the real Android TUN fd, wrapped by bufferedTun in
// fulltunnel.go. Every app's traffic — plain passthrough, MITM'd HTTPS,
// DNS — is encapsulated as IP packets by gVisor and read/written on that
// one fd, so limiting there limits everything uniformly without having
// to patch every flow handler individually.
//
//   Read()  (bufferedTun) = packets FROM apps TO the internet  → upload
//   Write() (bufferedTun) = packets FROM the internet TO apps  → download
//
// A rate of 0 (or negative) means "unlimited" and Wait() becomes a
// no-op, so there is no overhead when throttling is turned off.
// ─────────────────────────────────────────────────────────────────────────────

// bandwidthLimiter wraps golang.org/x/time/rate.Limiter with a byte-count
// API and a simple on/off switch, safe for concurrent use.
type bandwidthLimiter struct {
	mu      sync.RWMutex
	limiter *rate.Limiter
	kbps    int
}

func newBandwidthLimiter() *bandwidthLimiter {
	return &bandwidthLimiter{}
}

// SetKbps configures the limit in kilobytes/second. 0 or a negative
// value disables throttling for this direction.
func (b *bandwidthLimiter) SetKbps(kbps int) {
	b.mu.Lock()
	defer b.mu.Unlock()

	b.kbps = kbps
	if kbps <= 0 {
		b.limiter = nil
		return
	}

	bytesPerSec := kbps * 1024
	// Burst needs to comfortably cover a handful of max-size packets so
	// a single Read/Write call is never rejected outright — it should
	// only ever have to wait, never fail.
	burst := bytesPerSec
	if burst < 65536 {
		burst = 65536
	}
	b.limiter = rate.NewLimiter(rate.Limit(bytesPerSec), burst)
}

// GetKbps returns the currently configured limit (0 = unlimited).
func (b *bandwidthLimiter) GetKbps() int {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return b.kbps
}

// Wait blocks until n bytes' worth of tokens are available. It is a
// no-op when unlimited. n is clamped to the limiter's burst so a single
// oversized read/write can never make WaitN return an error.
func (b *bandwidthLimiter) Wait(n int) {
	if n <= 0 {
		return
	}
	b.mu.RLock()
	l := b.limiter
	b.mu.RUnlock()

	if l == nil {
		return
	}
	if burst := l.Burst(); n > burst {
		n = burst
	}
	_ = l.WaitN(context.Background(), n)
}

// throttledWriter wraps an io.Writer so every Write call is paced by
// limiter. Used to throttle one direction of a per-flow relay
// (upload: writes to the remote/server side; download: writes to the
// client/app side). Blocking in Write is intentional here — these
// wrappers are only ever used inside per-flow goroutines (never on
// the shared TUN dispatch path), so pacing them is safe.
type throttledWriter struct {
	w       io.Writer
	limiter *bandwidthLimiter
}

func (t *throttledWriter) Write(p []byte) (int, error) {
	if t.limiter != nil {
		t.limiter.Wait(len(p))
	}
	return t.w.Write(p)
}

// throttle wraps w with limiter, unless limiter is nil (unlimited /
// whitelisted app), in which case w is returned unchanged.
func throttle(w io.Writer, limiter *bandwidthLimiter) io.Writer {
	if limiter == nil {
		return w
	}
	return &throttledWriter{w: w, limiter: limiter}
}
