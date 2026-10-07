#[derive(Debug, Clone, PartialEq)]
pub enum GL_Vdf_Value {
    Str(String),
    Obj(Vec<(String, GL_Vdf_Value)>),
}

impl GL_Vdf_Value {
    pub fn GL_Get(&self, key: &str) -> Option<&GL_Vdf_Value> {
        match self {
            GL_Vdf_Value::Obj(items) => items
                .iter()
                .find(|(k, _)| k.eq_ignore_ascii_case(key))
                .map(|(_, v)| v),
            GL_Vdf_Value::Str(_) => None,
        }
    }

    pub fn GL_Text(&self) -> Option<&str> {
        match self {
            GL_Vdf_Value::Str(s) => Some(s),
            GL_Vdf_Value::Obj(_) => None,
        }
    }

    pub fn GL_Entries(&self) -> &[(String, GL_Vdf_Value)] {
        match self {
            GL_Vdf_Value::Obj(items) => items,
            GL_Vdf_Value::Str(_) => &[],
        }
    }
}

enum GL_Vdf_Token {
    Str(String),
    Open,
    Close,
}

fn GL_Vdf_Tokenize(src: &str) -> Vec<GL_Vdf_Token> {
    let mut out = Vec::new();
    let mut chars = src.chars().peekable();
    while let Some(c) = chars.next() {
        match c {
            '{' => out.push(GL_Vdf_Token::Open),
            '}' => out.push(GL_Vdf_Token::Close),
            '"' => {
                let mut s = String::new();
                while let Some(c) = chars.next() {
                    match c {
                        '"' => break,
                        '\\' => match chars.next() {
                            Some('n') => s.push('\n'),
                            Some('t') => s.push('\t'),
                            Some(other) => s.push(other),
                            None => break,
                        },
                        _ => s.push(c),
                    }
                }
                out.push(GL_Vdf_Token::Str(s));
            }
            '/' if chars.peek() == Some(&'/') => {
                for c in chars.by_ref() {
                    if c == '\n' {
                        break;
                    }
                }
            }
            c if c.is_whitespace() => {}
            _ => {
                let mut s = String::from(c);
                while let Some(&n) = chars.peek() {
                    if n.is_whitespace() || n == '{' || n == '}' || n == '"' {
                        break;
                    }
                    s.push(n);
                    chars.next();
                }
                out.push(GL_Vdf_Token::Str(s));
            }
        }
    }
    out
}

pub fn GL_Vdf_Parse(src: &str) -> GL_Vdf_Value {
    let tokens = GL_Vdf_Tokenize(src);
    let mut pos = 0;
    GL_Vdf_Value::Obj(GL_Vdf_Object(&tokens, &mut pos))
}

fn GL_Vdf_Object(tokens: &[GL_Vdf_Token], pos: &mut usize) -> Vec<(String, GL_Vdf_Value)> {
    let mut items = Vec::new();
    while *pos < tokens.len() {
        match &tokens[*pos] {
            GL_Vdf_Token::Close => {
                *pos += 1;
                break;
            }
            GL_Vdf_Token::Open => *pos += 1,
            GL_Vdf_Token::Str(key) => {
                let key = key.clone();
                *pos += 1;
                match tokens.get(*pos) {
                    Some(GL_Vdf_Token::Open) => {
                        *pos += 1;
                        let value = GL_Vdf_Object(tokens, pos);
                        items.push((key, GL_Vdf_Value::Obj(value)));
                    }
                    Some(GL_Vdf_Token::Str(value)) => {
                        items.push((key, GL_Vdf_Value::Str(value.clone())));
                        *pos += 1;
                    }
                    _ => break,
                }
            }
        }
    }
    items
}
