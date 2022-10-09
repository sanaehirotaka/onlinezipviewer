class Tools {

	static books = {};

	static fileToUint8Array(file) {
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

	position() {
		if (this.index < 0)
			return 0;
		return (this.index + 1) / this.files.length;
	}

	close() {
		this.unzip = null;
		this.files = null;
	}
}

class Viewer {
	static default = Viewer.fromSelector("#viewer");

	books = {};
	root;
	canvas;
	context;
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
		this.progress();
	}

	init() {
		let root = this.root();
		this.close();
		if (root && !this.canvas) {
			let outer = root.querySelector(".viewer-canvas-outer");
			outer.addEventListener("click", e => {
				let rect = outer.getBoundingClientRect();
				(e.offsetX > rect.width / 2) ? this.next() : this.previous();
			});
			outer.addEventListener("wheel", e => {
				e.deltaY > 0 ? this.next() : this.previous();
			});
			outer.addEventListener("touchmove",e => {
				e.preventDefault();
			});
			outer.addEventListener("touchstart", e => {
				e.preventDefault();
			});
			outer.addEventListener("touchend", e => {
				let rect = outer.getBoundingClientRect();
				(e.changedTouches[0].clientX > rect.width / 2) ? this.next() : this.previous();
			});
			this.canvas = root.querySelector(".viewer-canvas");
		}
	}

	async open(book) {
		if (this.book) {
			this.book.close();
		}
		if (!book) {
			for (let name of Object.keys(this.books)) {
				book = this.books[name];
			}
		}
		this.book = book;
		await book.open();
		this.title();

		this.next();
		this.progress();
	}

	async close() {
		if (this.book) {
			this.book.close();
		}
		this.book = null;
		this.title();
		await this.write(null);
		this.progress();
	}

	title() {
		if (this.book) {
			document.title = this.book.name;
		} else {
			document.title = "";
		}
	}

	add(book) {
		this.books[book.name] = book;
		let root = this.root();
		if (root) {
			let menu = root.querySelector(".viewer-history");
			menu.innerHTML = "";
			for (let name of Object.keys(this.books)) {
				let item = document.createElement("li");
				let link = document.createElement("a");
				link.setAttribute("class", "btn dropdown-item");
				link.appendChild(document.createTextNode(name));
				link.addEventListener("click", e => {
					this.open(this.books[name]);
				});
				item.appendChild(link);
				menu.appendChild(item);
			}
			{
				let item = document.createElement("li");
				let hr = document.createElement("hr");
				hr.setAttribute("class", "dropdown-divider");
				item.appendChild(hr);
				menu.appendChild(item);

			}
			{
				let item = document.createElement("li");
				let link = document.createElement("a");
				link.setAttribute("class", "btn dropdown-item");
				link.appendChild(document.createTextNode("開いた本を閉じる"));
				link.addEventListener("click", async e => {
					await this.close();
				});
				item.appendChild(link);
				menu.appendChild(item);
			}
		}
	}

	progress() {
		let root = this.root();
		if (root) {
			let progress = root.querySelector(".viewer-progress .progress-bar");
			if (this.book) {
				progress.setAttribute("style", `width: ${this.book.position() * 100}%`);
			} else {
				progress.setAttribute("style", "width: 0%");
			}
		}
	}

	next() {
		if (this.book) {
			this.write(this.book.next());
			this.progress();
		}
	}

	previous() {
		if (this.book) {
			this.write(this.book.previous());
			this.progress();
		}
	}

	async write(page) {
		if (!page) {
			if (this.canvas) {
				let ctx = this.canvas.getContext("2d");
				ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
			}
			return;
		}

		let blob = new Blob([page], { type: "application/octet-binary" });
		let rect = this.canvas.parentNode.getBoundingClientRect();
		if (this.canvas.width != parseInt(rect.width) || this.canvas.height != parseInt(rect.height)) {
			this.canvas.width = rect.width;
			this.canvas.height = rect.height;
			this.context = this.canvas.getContext("2d");
		}
		if (!this.context) {
			this.context = this.canvas.getContext("2d");
		}
		try {
			let bitmap = await createImageBitmap(blob);

			let zoom = Math.min(this.canvas.width / bitmap.width, this.canvas.height / bitmap.height);

			let width = zoom * bitmap.width;
			let height = zoom * bitmap.height;
			let startX = (this.canvas.width - width) / 2;
			let startY = (this.canvas.height - height) / 2;

			this.context.clearRect(0, 0, this.canvas.width, this.canvas.height);
			this.context.drawImage(bitmap, startX, startY, width, height);

		} catch (ex) {
  			this.context.font = '18px sans-serif';
  			this.context.fillStyle = "red";
			this.context.clearRect(0, 0, this.canvas.width, this.canvas.height);
			this.context.fillText(ex.toString(), 2, 32);
		}
	}
}

class Events {

	static init() {
		window.addEventListener("DOMContentLoaded", Events.loaded);
		window.addEventListener("dragstart", Events.dragstart);
		window.addEventListener("dragenter", Events.dragenter);
		window.addEventListener("dragover", Events.dragover);
		window.addEventListener("drop", Events.drop);
	}
	static loaded(e) {
		Viewer.getDefault().init();
		UI.init();
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
		let viewer = Viewer.getDefault();
		for (let file of e.dataTransfer.files) {
			let book = await Book.fromFile(file);

			viewer.init();
			viewer.add(book);
		}
		await viewer.open();
	}
}
class UI {

	static token;

	static init() {
		document.querySelector("#login").addEventListener("click", UI.login);
		document.querySelector("#loginForm").addEventListener("submit", e => {
			e.preventDefault();
			return false;
		});
		document.querySelector("#selectModal").addEventListener("show.bs.modal", UI.selectInit);
	}

	static loginOK() {
		document.querySelector("#loginButton").classList.add("d-none");
		document.querySelector("#selectButton").classList.remove("d-none");

	}

	static loginNG() {
		document.querySelector("#loginButton").classList.remove("d-none");
		document.querySelector("#loginButton").classList.remove("btn-primary");
		document.querySelector("#loginButton").classList.add("btn-warning");
		document.querySelector("#selectButton").classList.add("d-none");
		UI.token = null;
	}

	static async login(e) {
		let res = await fetch("/api/login/getToken", {
			method: 'POST',
			body: new FormData(document.querySelector("#loginForm"))
		});
		if (res.ok) {
			UI.loginOK();
			UI.token = (await res.json()).token;
		} else {
			UI.loginNG();
		}
	}

	static async selectInit() {
		let form = new FormData();
		form.append("token", UI.token);

		let res = await fetch("/api/storage/getBukets", {
			method: 'POST',
			body: form
		});

		if (res.ok) {
			let sel = document.querySelector("#bucketSelect");
			for (let bucket of await res.json()) {
				let op = document.createElement("option");
				op.setAttribute("value", bucket);
				op.appendChild(document.createTextNode(bucket));
				sel.appendChild(op);
			}
		} else {
			UI.loginNG();
		}

		console.log(res);
	}
}
Events.init();
