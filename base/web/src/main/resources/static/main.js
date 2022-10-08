/**
 *
 */

class Events {

	static init() {
		window.addEventListener("DOMContentLoaded", Events.loaded);
		window.addEventListener("dragstart", Events.dragstart);
		window.addEventListener("dragenter", Events.dragenter);
		window.addEventListener("dragover", Events.dragover);
		window.addEventListener("drop", Events.drop);
	}
	static loaded(e) {
	}
	static dragstart(e) {
		e.preventDefault();
	}
	static dragenter(e) {
		e.preventDefault();
	}
	static dragover(e) {
		e.preventDefault();
	}
	static async drop(e) {
		e.preventDefault();
		for (let file of e.dataTransfer.files) {
			let book = await Book.fromFile(file);

			let viewer = Viewer.getDefault();
			viewer.init();
			await viewer.open(book);
		}
	}
}

class Tools {
	
	static fileToUint8Array (file) {
		return new Promise((resolve, reject) => {
			let reader = new FileReader();
			reader.onload = () => {
				resolve(new Uint8Array(reader.result));
				reader.onload = null;
			}
			reader.readAsArrayBuffer(file);
		});
	}
}

class Book {
	name;
	content;

	unzip;
	files;

	index;

	static async fromFile(file) {
		let book = new Book();
		book.name = file.name;
		book.content = await Tools.fileToUint8Array(file);
		return book;
	}

	async open() {
		this.unzip = new Zlib.Unzip(this.content);
		this.files = this.unzip.getFilenames();
		this.index = -1;
	}

	next() {
		this.index = Math.min(this.index + 1, this.files.length - 1);
		return this.unzip.decompress(this.files[this.index]);
	}

	previous() {
		this.index = Math.max(this.index - 1, 0);
		return this.unzip.decompress(this.files[this.index]);
	}

	close() {
		this.unzip = null;
		this.files = null;
	}
}

class Viewer {
	static default = Viewer.fromSelector("#viewer");

	root;
	canvas;
	book;

	static fromSelector(selector) {
		return new Viewer(() => document.querySelector(selector));
	}

	static getDefault() {
		return Viewer.default;
	}

	constructor(element) {
		this.root = element;
		this.init();
	}

	init() {
		let root = this.root();
		if (this.book) {
			this.book.close();
		}
		this.book = null;
		if (root && !this.canvas) {
			let canvas = document.createElement("canvas");
			canvas.setAttribute("class", "viewer-canvas");
			canvas.addEventListener("click", () => this.next())
			root.appendChild(canvas);
			this.canvas = canvas;
		}
	}

	async open(book) {
		this.book = book;
		await book.open();
	}

	next() {
		if (this.book) {
			let page = this.book.next();
			this.write(page);
		}
	}

	async write(page) {
		let blob = new Blob([page], {type: "application/octet-binary"});
		let ctx = this.canvas.getContext("2d");
		let bitmap = await createImageBitmap(blob);

		let zoom = Math.min(this.canvas.width / bitmap.width, this.canvas.height / bitmap.height);

		let width = zoom * bitmap.width;
		let height = zoom * bitmap.height;
		ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
		ctx.drawImage(bitmap, (this.canvas.width - width) / 2, (this.canvas.height - height) / 2, width, height);
	}
}

Events.init();
