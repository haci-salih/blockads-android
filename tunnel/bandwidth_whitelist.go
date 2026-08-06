package tunnel

import (
	"strconv"
	"strings"
	"sync"
)

// ─────────────────────────────────────────────────────────────────────────────
// bandwidth_whitelist.go — apps exempt from the global bandwidth limiter.
//
// Kotlin lets the user pick specific apps that should NEVER be
// throttled, even while a global SetBandwidthLimitKbps cap is active
// for everything else. The whitelist is keyed by Android app UID
// (the same UID resolveFlowUID() already produces for every flow), so
// checking it costs one map lookup per flow — done once when the flow
// is set up, not per packet.
// ─────────────────────────────────────────────────────────────────────────────

// uidSet is a simple thread-safe set of app UIDs.
type uidSet struct {
	mu  sync.RWMutex
	set map[int]struct{}
}

func newUIDSet() *uidSet {
	return &uidSet{set: make(map[int]struct{})}
}

// SetFromCSV replaces the set's contents from a comma-separated list
// of UIDs (e.g. "10123,10456,10789"). Empty/whitespace entries and
// entries that don't parse as integers are silently skipped. Passing
// an empty string clears the whitelist entirely.
//
// A plain string is used (rather than a slice) because gomobile bind
// does not support exporting []int to Kotlin — only primitives,
// strings, and []byte cross that boundary. This mirrors the existing
// SetSplitDNSZones(zones string) convention in engine.go.
func (s *uidSet) SetFromCSV(csv string) {
	next := make(map[int]struct{})
	for _, part := range strings.Split(csv, ",") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		if uid, err := strconv.Atoi(part); err == nil {
			next[uid] = struct{}{}
		}
	}

	s.mu.Lock()
	s.set = next
	s.mu.Unlock()
}

// Contains reports whether uid is in the whitelist (i.e. should be
// exempt from bandwidth throttling).
func (s *uidSet) Contains(uid int) bool {
	if uid == UIDUnknown {
		return false
	}
	s.mu.RLock()
	defer s.mu.RUnlock()
	_, ok := s.set[uid]
	return ok
}
