package org.redisson.cache;

import org.redisson.misc.WrappedLock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe queue with O(1) complexity for removal operation.
 *
 * @author Nikita Koksharov
 *
 * @param <E> element type
 */
public final class FastRemovalQueue<E> {

    private final Map<E, Node<E>> index = new ConcurrentHashMap<>();
    private final DoublyLinkedList<E> list = new DoublyLinkedList<>();

    public void add(E element) {
        Node<E> newNode = new Node<>(element);
        if (index.putIfAbsent(element, newNode) == null) {
            list.add(newNode);
        }
    }

    public boolean moveToTail(E element) {
        Node<E> node = index.get(element);
        if (node != null) {
            list.moveToTail(node);
            return true;
        }
        return false;
    }

    public boolean remove(E element) {
        Node<E> node = index.remove(element);
        if (node != null) {
            return list.remove(node);
        }
        return false;
    }

    public E poll() {
        Node<E> node = list.removeFirst();
        if (node != null) {
            index.remove(node.value);
            return node.value;
        }
        return null;
    }

    public void clear() {
        index.clear();
        list.clear();
    }

    static class Node<E> {
        private final E value;
        private Node<E> prev;
        private Node<E> next;
        private boolean deleted;

        public Node(E value) {
            this.value = value;
        }

        public void setDeleted() {
            deleted = true;
        }

        public boolean isDeleted() {
            return deleted;
        }
    }

    static class DoublyLinkedList<E> {
        private final WrappedLock lock = new WrappedLock();
        private Node<E> head;
        private Node<E> tail;

        public DoublyLinkedList() {
        }

        public void clear() {
            lock.execute(() -> {
                head = null;
                tail = null;
            });
        }

        public void add(Node<E> newNode) {
            lock.execute(() -> {
                addNode(newNode);
            });
        }

        private void addNode(Node<E> newNode) {
            Node<E> currentTail = tail;
            tail = newNode;
            if (currentTail == null) {
                head = newNode;
            } else {
                newNode.prev = currentTail;
                currentTail.next = newNode;
            }
        }

        public boolean remove(Node<E> node) {
            return lock.execute(() -> {
                if (node.isDeleted()) {
                    return false;
                }

                removeNode(node);
                node.setDeleted();
                return true;
            });
        }

        private void removeNode(Node<E> node) {
            Node<E> prevNode = node.prev;
            Node<E> nextNode = node.next;

            if (prevNode != null) {
                prevNode.next = nextNode;
            } else {
                head = nextNode;
            }

            if (nextNode != null) {
                nextNode.prev = prevNode;
            } else {
                tail = prevNode;
            }
        }

        public void moveToTail(Node<E> node) {
            lock.execute(() -> {
                removeNode(node);

                node.prev = null;
                node.next = null;
                addNode(node);
            });
        }

        public Node<E> removeFirst() {
            return lock.execute(() -> {
                Node<E> currentHead = head;
                if (head == tail) {
                    head = null;
                    tail = null;
                } else {
                    head = head.next;
                    head.prev = null;
                }
                if (currentHead != null) {
                    currentHead.setDeleted();
                }
                return currentHead;
            });
        }

    }
}