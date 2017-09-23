package collection.hashgold;

import java.util.ArrayList;

/**
 * ������ϣ������ȡԪ�أ��޶����ϳ���
 */

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

public class LimitedRandomSet<T> {
	private int len;// ����б��ȣ�0������
	private final HashSet<T> holdSet;	// ��ʼ������BEGIN
	public LimitedRandomSet() throws Exception {
		this(0);
	}

	/**
	 * ָ���б��������
	 * 
	 * @param max_length
	 *            ���ڵ�����
	 * @throws Exception
	 */
	public LimitedRandomSet(int max_length) throws Exception {
		if (max_length < 0) {
			throw new Exception("Random set length error");
		}
		len = max_length;
		holdSet = new HashSet<T>();
	}
	
	/**
	 * �ø������ϳ�ʼ��
	 * @param collection
	 */
	public LimitedRandomSet(Collection<T> collection) {
		holdSet = new HashSet<T>(collection);
	}

	
	/**
	 * �ø������ϳ�ʼ�������Ƴ���
	 * @param collection
	 * @param max_length
	 * @throws Exception
	 */
	public LimitedRandomSet(Collection<T> collection, int max_length) throws Exception {
		this(collection);
		if (max_length < 0) {
			throw new Exception("Random set length error");
		}
		len = max_length;
	}


	/**
	 * ���һ������
	 * 
	 * @param elements
	 * @return HashSet<T> �����Ԫ�صļ���
	 */
	public Set<T> add(Set<T> elements) {
		return add(elements, null);
	}

	/**
	 * ���һ�����ϲ�ָ��������
	 * 
	 * @param elements
	 * @return HashSet<T> �����Ԫ�صļ���
	 */
	 synchronized public Set<T> add(Collection<T> elements, Predicate<T> filter) {
		Set<T> newElements = new HashSet<T>();

		// find out new elements
		Iterator<T> it = elements.iterator();
		while (it.hasNext()) {
			T element = it.next();
			if (!holdSet.contains(element)) {
				newElements.add(element);
			}
		}

		// filter new elements to add
		if (filter != null) {
			newElements.removeIf(filter);
		}

		// add new elements to local set asynchronously
		if (!newElements.isEmpty()) {
			holdSet.addAll(newElements);
			if (holdSet.size() > len) {
				try {
					Set<T> toRemove = pick(holdSet.size() - len);
					holdSet.removeAll(toRemove);
					newElements.removeAll(toRemove);
				} catch (Exception e) {
				}

			}
		}

		return newElements;
	}

	/**
	 * pick some randomized elements from the set
	 * 
	 * @param n
	 * @return
	 * @throws Exception
	 */
	 synchronized public Set<T> pick(int n) throws Exception {
		if (n <= 0) {
			throw new Exception("Pick amount must be positive");
		}

		Set<T> pickedElements = new HashSet<T>();
		Set<Integer> indexesToPick = new HashSet<Integer>();
		Random rand = new Random();
		
		List<T> holdList = new ArrayList<T>(holdSet);

		do {
			indexesToPick.add(rand.nextInt(holdList.size()));
		} while (indexesToPick.size() < n);
		
		Iterator<Integer> it = indexesToPick.iterator();
		while (it.hasNext()) {
			pickedElements.add(holdList.get(it.next()));
		}

		

		return pickedElements;
	}

	/**
	 * Remove an element from the set
	 * 
	 * @param element
	 * @return
	 */
	 synchronized public boolean remove(T element) {
		return holdSet.remove(element);
	}

	/**
	 * Delete elements
	 * 
	 * @param elements
	 * @return
	 */
	synchronized public boolean remove(HashSet<T> elements) {
		return holdSet.removeAll(elements);
	}

	/**
	 * Add an element to the set
	 * 
	 * @param element
	 * @return
	 */
	synchronized public boolean add(T element) {
		Set<T> set = new HashSet<T>();
		set.add(element);
		if (holdSet.size() < len) {
			return !add(set).isEmpty();
		} else {
			return false;
		}
	}

	
	/**
	 * get size
	 * 
	 * @return
	 */
	public int size() {
		return holdSet.size();
	}

	public boolean contains(T element) {
		return holdSet.contains(element);
	}
}
