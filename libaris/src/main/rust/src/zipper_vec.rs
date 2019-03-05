/// a ZipperVec represents a list-with-edit-position [a,b,c, EDIT_CURSOR, d, e, f] as (vec![a, b, c], vec![f, e, d])
/// since Vec's have O(1) insert/remove at the end, ZipperVec's have O(1) insert/removal around the edit cursor, while being way more cache/memory efficient than a doubly-linked list
/// the cursor can be moved from position i to position j in O(|i-j|) time by shuffling elements between the prefix and the suffix
// TODO: should Eq for ZipperVec quotient out cursor position?
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ZipperVec<T> {
    prefix: Vec<T>,
    suffix_r: Vec<T>,
}
impl<T> ZipperVec<T> {
    pub fn new() -> Self { ZipperVec { prefix: Vec::new(), suffix_r: Vec::new() } }
    pub fn from_vec(v: Vec<T>) -> Self { ZipperVec { prefix: v, suffix_r: Vec::new() } }
    pub fn cursor_pos(&self) -> usize { self.prefix.len() }
    pub fn len(&self) -> usize { self.prefix.len() + self.suffix_r.len() }
    pub fn inc_cursor(&mut self) { if let Some(x) = self.prefix.pop() { self.suffix_r.push(x); } }
    pub fn dec_cursor(&mut self) { if let Some(x) = self.suffix_r.pop() { self.prefix.push(x); } }
    pub fn move_cursor(&mut self, mut to: usize) {
        while to > self.cursor_pos() { self.dec_cursor(); to -= 1 }
        while to < self.cursor_pos() { self.inc_cursor(); to += 1 }
    }
    pub fn push(&mut self, x: T) {
        let len = self.len();
        self.move_cursor(len);
        self.prefix.push(x);
    }
    pub fn iter(&self) -> impl Iterator<Item=&T> {
        self.prefix.iter().chain(self.suffix_r.iter().rev())
    }
}

impl<T: PartialEq> ZipperVec<T> {
    pub fn insert_relative(&mut self, val: T, rel: &T, after: bool) {
        if self.len() == 0 {
            self.prefix.push(val);
        } else {
            self.move_cursor(0); // TODO: try to insert while backwards sweeping to 0 for more efficiency
            while self.suffix_r.len() > 0 && &self.suffix_r[self.suffix_r.len()-1] != rel {
                self.inc_cursor();
            }
            (if after { &mut self.suffix_r } else { &mut self.prefix }).push(val);
        }
    }
}
