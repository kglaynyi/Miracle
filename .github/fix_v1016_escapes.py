from pathlib import Path

p = Path('app/src/main/java/com/miracle/kglaynyi/utils/SendGetRequestTMDB.java')
s = p.read_text()
s = s.replace(r'.replaceAll("[^\p{L}\p{N}]+", " ")',
              r'.replaceAll("[^\\p{L}\\p{N}]+", " ")')
s = s.replace(r'.replaceAll("\s+", " ").trim()',
              r'.replaceAll("\\s+", " ").trim()')
p.write_text(s)
print('Java regex escapes repaired')
