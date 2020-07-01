/// A ZipperVec represents a list-with-edit-position [a,b,c, EDIT_CURSOR, d, e, f] as (vec![a, b, c], vec![f, e, d]).
/// Since Vec's have O(1) insert/remove at the end, ZipperVec's have O(1) insert/removal around the edit cursor, while being way more cache/memory efficient than a doubly-linked list.
/// The cursor can be moved from position i to position j in O(|i-j|) time by shuffling elements between the prefix and the suffix.
// TODO: should Eq for ZipperVec quotient out cursor position?
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ZipperVec<T> {
    prefix: Vec<T>,
    suffix_r: Vec<T>,
}

/*
impl<T> std::fmt::Debug for ZipperVec<T> {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        f.debug_struct("ZipperVec")
            .field("prefix", &self.prefix.iter().map(|_| ()).collect::<Vec<_>>())
            .field("suffix_r", &self.suffix_r.iter().map(|_| ()).collect::<Vec<_>>())
            .finish()
    }
}
*/

impl<T> ZipperVec<T> {
    pub fn new() -> Self { ZipperVec { prefix: Vec::new(), suffix_r: Vec::new() } }
    pub fn from_vec(v: Vec<T>) -> Self { ZipperVec { prefix: v, suffix_r: Vec::new() } }
    pub fn cursor_pos(&self) -> usize { self.prefix.len() }
    pub fn len(&self) -> usize { self.prefix.len() + self.suffix_r.len() }
    pub fn is_empty(&self) -> bool { self.len() == 0 }
    pub fn dec_cursor(&mut self) { if let Some(x) = self.prefix.pop() { self.suffix_r.push(x); } }
    pub fn inc_cursor(&mut self) { if let Some(x) = self.suffix_r.pop() { self.prefix.push(x); } }
    pub fn move_cursor(&mut self, to: usize) {
        assert!(to <= self.len());
        while to > self.cursor_pos() { self.inc_cursor(); }
        while to < self.cursor_pos() { self.dec_cursor(); }
    }
    pub fn push(&mut self, x: T) {
        let len = self.len();
        self.move_cursor(len);
        self.prefix.push(x);
    }
    pub fn push_front(&mut self, x: T) {
        self.move_cursor(0);
        self.prefix.push(x);
    }
    pub fn iter(&self) -> impl Iterator<Item=&T> {
        self.prefix.iter().chain(self.suffix_r.iter().rev())
    }
    pub fn get(&self, i: usize) -> Option<&T> {
        let j = self.cursor_pos();
        let k = self.suffix_r.len();
        if i < j {
            self.prefix.get(i)
        } else if i - j < k {
            self.suffix_r.get(i - j)
        } else {
            None
        }
    }
    pub fn pop(&mut self, i: usize) -> Option<T> {
        if i < self.len() {
            self.move_cursor(i);
            self.suffix_r.pop()
        } else {
            None
        }
    }
}

impl<T> Default for ZipperVec<T> {
    fn default() -> Self {
        Self::new()
    }
}

#[test]
fn test_zippervec_pop() {
    let a = ZipperVec::from_vec((0usize..10).into_iter().collect());
    let pop_spec = |i| -> (Option<usize>, Vec<usize>) { (Some(a.iter().cloned().collect::<Vec<usize>>()[i]), a.iter().enumerate().filter_map(|(j, y)| if i == j { None } else { Some(*y) }).collect::<Vec<_>>()) };
    for i in 0..10 {
        for j in 0..10 {
            let mut b = a.clone();
            b.move_cursor(i);
            assert_eq!(b.cursor_pos(), i);
            let x = b.pop(j);
            let (y, z) = pop_spec(j);
            assert_eq!(x, y);
            assert_eq!(b.iter().cloned().collect::<Vec<usize>>(), z);
        }
    }
}

impl<T: PartialEq> ZipperVec<T> {
    pub fn insert_relative(&mut self, val: T, rel: &T, after: bool) {
        if self.is_empty() {
            self.prefix.push(val);
        } else {
            self.move_cursor(0); // TODO: try to insert while backwards sweeping to 0 for more efficiency
            while !self.suffix_r.is_empty() && &self.suffix_r[self.suffix_r.len()-1] != rel {
                self.inc_cursor();
            }
            if after { self.inc_cursor(); }
            (if after { &mut self.suffix_r } else { &mut self.prefix }).push(val);
        }
    }
}

#[test]
fn test_zippervec_insert_relative() {
    let mut z = ZipperVec::from_vec(vec![0,10,20,30,40]);
    println!("{:?}", z);
    z.insert_relative(25, &20, true);
    println!("{:?}", z);
    assert_eq!(z.iter().cloned().collect::<Vec<usize>>(), vec![0,10,20,25,30,40]);
    z.insert_relative(26, &30, false);
    println!("{:?}", z);
    assert_eq!(z.iter().cloned().collect::<Vec<usize>>(), vec![0,10,20,25,26,30,40]);
    z.insert_relative(15, &10, true);
    println!("{:?}", z);
    assert_eq!(z.iter().cloned().collect::<Vec<usize>>(), vec![0,10,15,20,25,26,30,40]);
}
