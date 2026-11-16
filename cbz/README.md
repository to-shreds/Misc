# Comic Library books

Upload `.cbz` or `.pdf` books here. They appear automatically in the
Arcade's Comic Library: https://to-shreds.github.io/arcade/comic-reader/

Name the file how you want it to appear, such as `Book 01.cbz`.
The reader also opens local CBZ and PDF files without uploading them.

## Books too large for normal GitHub uploads

GitHub's browser upload limit is 25 MiB per file; ordinary Git pushes
allow up to 100 MiB per file. The reader itself does **not** have a
150 MiB whole-book cap.

For a larger book, divide the original file into consecutive **raw byte
chunks** of no more than 24 MiB, named like these:

```
My Book.cbz.001
My Book.cbz.002
My Book.cbz.003
```

Upload all the numbered files together into this folder. The Arcade
automatically shows them as one book and reassembles them when opened.
This also works for `My Book.pdf.001`, `.002`, and so on. The chunks
must be unmodified slices of the original file, not new ZIP archives.
Do not use the 7-Zip multi-volume archive option.

The helper `comic-reader/split-book.py` is included in the Arcade source
repository to generate correctly sized parts. For example:

`python split-book.py "My Book.cbz" --output parts`

Remember that every file committed here is **public**.
GitHub Releases support bigger assets but are not automatically
indexed by this folder's bookshelf.
