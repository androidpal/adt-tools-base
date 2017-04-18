/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#ifndef PRODUCER_CONSUMER_QUEUE_H
#define PRODUCER_CONSUMER_QUEUE_H

#include <atomic>
#include <cassert>
#include <condition_variable>
#include <deque>
#include <mutex>

namespace profiler {

// A blocking synchronized producer-consumer queue that also supports
// move-only types. Optionally accepts a |max_length| that is expected to be
// greater than zero, which allows the queue to be bounded. Otherwise, the queue
// can grow unbounded.
// NOTE the current policy is to discard the oldest data when the queue is full.
// TODO abstract out the policy to support other logic as needed (e.g. blocking
// when full).
//
// Example:
//   ProducerConsumerQueue<int32_t> q;
//
//   In thread #1
//   ============
//   int val;
//   // |Pop| will block until a value is available or the channel is finished
//   while (q.Pop(&val)) {
//     ...
//   }
//
//   In thread #2
//   ============
//   q.Push(long_operation_1());
//   q.Push(long_operation_2());
//   q.Push(long_operation_3());
//   q.Push(long_operation_4());
//   q.Finish();
template <typename T>
class ProducerConsumerQueue {
 public:
  ProducerConsumerQueue(int32_t max_length = -1)
      : max_length_(max_length), is_finished(false) {
    assert(max_length_ > 0 || max_length_ == -1);
  }

  // No copy or move semantics
  ProducerConsumerQueue(const ProducerConsumerQueue&) = delete;
  ProducerConsumerQueue& operator=(const ProducerConsumerQueue&) = delete;

  // Push a value into the queue. Values will be consumed in the order entered
  // by calls to |Pop|. If |Finish| was called on this channel, then the value
  // entered here will be ignored (and |false| will be returned to indicate it).
  template <class VALUE>
  bool Push(VALUE&& value) {
    std::lock_guard<std::mutex> lock(queue_mutex_);
    if (is_finished) {
      return false;
    }

    if (inner_queue_.empty()) {
      queue_cv_.notify_all();
    }

    // Removes oldest data to make room for value.
    if (max_length_ > 0 && inner_queue_.size() >= max_length_) {
      assert(inner_queue_.size() == max_length_);
      inner_queue_.pop_front();
    }

    inner_queue_.push_back(std::forward<VALUE>(value));
    return true;
  }

  // Pull a value out of the queue added by |Push|. If the queue is
  // currently empty, this call will block until a value is put in, unless
  // the queue was marked finished by calling |Finish|, at which point it will
  // exit immediately and return |false|.
  bool Pop(T* value) {
    std::unique_lock<std::mutex> lock(queue_mutex_);
    while (!is_finished && inner_queue_.empty()) {
      queue_cv_.wait(lock);
    }

    if (!inner_queue_.empty()) {
      *value = std::move(inner_queue_.front());
      inner_queue_.pop_front();
      return true;
    } else {
      assert(is_finished);
      return false;
    }
  }

  // Pulls all the contents of the queue at once.
  std::deque<T> Drain() {
    std::unique_lock<std::mutex> lock(queue_mutex_);
    std::deque<T> snapshot;
    std::swap(snapshot, inner_queue_);
    return snapshot;
  }

  // Indicate that this queue shouldn't accept values anymore. When calling
  // |Pop| on an empty queue that is finished, instead of blocking
  // indefinitely, the method will return |false| immediately. This allows
  // callers to pull data out of a queue in a while loop which will break
  // automatically when the queue is finished.
  void Finish() {
    std::lock_guard<std::mutex> lock(queue_mutex_);
    is_finished = true;
    queue_cv_.notify_all();
  }

 private:
  int32_t max_length_;
  bool is_finished;
  mutable std::condition_variable queue_cv_;
  mutable std::mutex queue_mutex_;
  std::deque<T> inner_queue_;
};

}  // namespace profiler

#endif  // PRODUCER_CONSUMER_QUEUE_H
