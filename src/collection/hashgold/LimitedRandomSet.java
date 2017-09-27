package collection.hashgold;

import java.util.ArrayList;

/**
 * 随机集合，随机存取元素，限定集合长度
 */

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

public class LimitedRandomSet<T> implements Set<T>{
	private final HashSet<T> holdSet;	// 初始化集合BEGIN
	private int len;// 最大列表长度，0不限制
	public LimitedRandomSet() throws Exception {
		this(0);
	}

	/**
	 * 用给定集合初始化
	 * @param collection
	 */
	public LimitedRandomSet(Collection<T> collection) {
		holdSet = new HashSet<T>(collection);
	}
	
	/**
	 * 用给定集合初始化并限制长度
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
	 * 指定列表最大容量
	 * 
	 * @param max_length
	 *            最大节点数量
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
	 * Add an element to the set
	 * 
	 * @param element
	 * @return
	 */
	synchronized public boolean add(T element) {
		Set<T> set = new HashSet<T>();
		set.add(element);
		if (holdSet.size() < len) {
			return !addAll(set, null).isEmpty();
		} else {
			return false;
		}
	}
	 
	
	 

	@Override
	public boolean addAll(Collection<? extends T> c) {
		return ! addAll((Collection<T>)c, null).isEmpty();
	}

	/**
	 * 添加一个集合并指定过滤器
	 * 
	 * @param elements
	 * @return HashSet<T> 新添加元素的集合
	 */
	 synchronized public Set<T> addAll(Collection<T> elements, Predicate<T> removeIfCondition) {
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
		if (removeIfCondition != null) {
			newElements.removeIf(removeIfCondition);
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

	@Override
	public void clear() {
		holdSet.clear();
		
	}

	@Override
	public boolean contains(Object element) {
		return holdSet.contains(element);
	}

	
	@Override
	public boolean containsAll(Collection<?> c) {
		return holdSet.containsAll(c);
	}

	@Override
	public boolean isEmpty() {
		return holdSet.isEmpty();
	}
	

	@Override
	public Iterator<T> iterator() {
		return holdSet.iterator();
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
		
		if (n >= holdSet.size()) {
			return holdSet;
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
	 synchronized public boolean remove(Object element) {
		return holdSet.remove(element);
	}

	

	@Override
	synchronized public boolean removeAll(Collection<?> c) {
		return holdSet.removeAll(c);
	}



	@Override
	public boolean retainAll(Collection<?> c) {
		return holdSet.retainAll(c);
	}

	/**
	 * get size
	 * 
	 * @return
	 */
	public int size() {
		return holdSet.size();
	}
	

	@Override
	public Object[] toArray() {
		return holdSet.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return null;
	}

	

	
}
