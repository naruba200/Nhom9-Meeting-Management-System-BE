
const token = localStorage.getItem("token");
console.log("Token hiện tại:", token);

// 1. Lấy thông tin người dùng
function loadUserInfo() {
    fetch("/api/user/me", {
        headers: { "Authorization": `Bearer ${token}` }
    })
    .then(res => {
        if (!res.ok) throw new Error("Không lấy được thông tin người dùng");
        return res.json();
    })
    .then(data => {
        document.getElementById("welcomeMessage").innerHTML =
            `<h3>Xin chào, ${data.fullName} (${data.email})</h3>`;
    })
    .catch(err => {
        console.error(err);
        document.getElementById("welcomeMessage").innerHTML =
            `<p>Vui lòng đăng nhập lại.</p>`;
    });
}

// 2. Load danh sách sản phẩm
function loadProducts() {
    fetch("/api/products", {
        headers: { "Authorization": `Bearer ${token}` }
    })
    .then(res => {
        if (!res.ok) throw new Error("Không thể lấy danh sách sản phẩm");
        return res.json();
    })
    .then(products => {
        

        const listDiv = document.getElementById("productList");
        listDiv.style.visibility = "visible";
        if (products.length === 0) {
            listDiv.innerHTML = "<p>Chưa có sản phẩm nào.</p>";
            return;
        }

        listDiv.innerHTML = ""; // reset cũ nếu có
        products.forEach(p => {
            const div = document.createElement("div");
            div.className = "product-card";
            div.innerHTML = `
                <img src="${p.imageUrl || 'https://via.placeholder.com/200'}" alt="${p.name}">
                <h4>${p.name}</h4>
                <p>${p.description || "Không có mô tả"}</p>
                <p><strong>${p.price.toLocaleString()} VND</strong></p>
                <button onclick="addToCart(${p.id})">Thêm vào giỏ</button>
            `;
            listDiv.appendChild(div);
        });
    })
    .catch(err => {
        console.error(err);
        document.getElementById("productList").innerHTML =
            `<p>Lỗi khi tải sản phẩm.</p>`;
    });
}

// 3. Thêm sản phẩm vào giỏ
function addToCart(productId) {
    fetch("/api/cart/add", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${token}`
        },
        body: JSON.stringify({ productId, quantity: 1 })
    })
    .then(res => {
        if (!res.ok) throw new Error("Không thể thêm vào giỏ");
        return res.text();
    })
    .then(msg => {
        alert(msg || "Đã thêm vào giỏ hàng");
        loadCart();
        loadCartHeader(); // cập nhật số lượng và nội dung ở box
    })
    .catch(err => {
        console.error(err);
        alert("Lỗi khi thêm sản phẩm vào giỏ");
    });
}

// 4. Hiển thị giỏ hàng trong trang chính
function loadCart() {
    fetch("/api/cart", {
        headers: { "Authorization": `Bearer ${token}` }
    })
    .then(res => {
if (!res.ok) throw new Error("Không thể lấy giỏ hàng");
        return res.json();
    })
    .then(cart => {
        const cartDiv = document.getElementById("cartItems");
        cartDiv.innerHTML = "";

        if (!cart.items || cart.items.length === 0) {
            cartDiv.innerHTML = "<p>Giỏ hàng trống.</p>";
            document.getElementById("totalPrice").innerText = "0";
            return;
        }

        cart.items.forEach(item => {
            const div = document.createElement("div");
            div.className = "product-card";
            div.innerHTML = `
                <img src="${item.imageUrl || 'https://via.placeholder.com/200'}" alt="${item.productName}">
                <h4>${item.productName}</h4>
                <p>Số lượng: ${item.quantity}</p>
                <p><strong>Giá: ${item.price.toLocaleString()} VND</strong></p>
            `;
            cartDiv.appendChild(div);
        });

        document.getElementById("totalPrice").innerText = cart.total.toLocaleString();
    })
    .catch(err => {
        console.error(err);
        document.getElementById("cartItems").innerHTML =
            `<p>Lỗi khi tải giỏ hàng.</p>`;
    });
}

// 5. Hiển thị giỏ hàng ở header popup
function loadCartHeader() {
    fetch("/api/cart", {
        headers: { "Authorization": `Bearer ${token}` }
    })
    .then(res => {
        if (!res.ok) throw new Error("Không thể lấy giỏ hàng");
        return res.json();
    })
    .then(cart => {
        const cartDiv = document.getElementById("cartItemsHeader");
        cartDiv.innerHTML = "";

        document.getElementById("cart-count").innerText = cart.items.length;

        if (!cart.items || cart.items.length === 0) {
            cartDiv.innerHTML = "<p>Giỏ hàng trống.</p>";
            document.getElementById("totalPriceHeader").innerText = "0";
            return;
        }

        cart.items.forEach(item => {
    const div = document.createElement("div");
    div.className = "cart-item";
    div.innerHTML = `
        <img src="${item.imageUrl || 'https://via.placeholder.com/100'}" alt="${item.productName}">
        <div class="cart-info">
            <h4>${item.productName}</h4>
            <p>Số lượng: ${item.quantity}</p>
            <p><strong>Giá: ${item.price.toLocaleString()} VND</strong></p>
        </div>
    `;
    cartDiv.appendChild(div);
});

        document.getElementById("totalPriceHeader").innerText = cart.total.toLocaleString();
    })
    .catch(err => {
        console.error(err);
        document.getElementById("cartItemsHeader").innerHTML =
            `<p>Lỗi khi tải giỏ hàng.</p>`;
    });
}

// 6. Mở/đóng popup giỏ hàng ở header
function opengiohang() {
    const box = document.getElementById("gioHangBox");
    box.style.display = (box.style.display === "none" || box.style.display === "") ? "block" : "none";
    loadCartHeader();
}
function closeGioHang() {
    document.getElementById("gioHangBox").style.display = "none";
}

// 7. Load khi trang sẵn sàng
window.onload = function () {
    loadUserInfo();
    loadProducts();
    loadCart();
    loadCartHeader();
};
let currentIndex = 0;
  const maxSlides = 3;

  function updateSlider() {
    const track = document.getElementById("mySliderTrack");
    track.style.transform = `translateX(-${currentIndex * 100}%)`;
  }

  function moveNext() {
    currentIndex = (currentIndex + 1) % maxSlides;
    updateSlider();
  }

  function movePrev() {
    currentIndex = (currentIndex - 1 + maxSlides) % maxSlides;
    updateSlider();
  }

  // Tự động chuyển ảnh sau mỗi 5 giây
  setInterval(moveNext, 5000);
  function openBox(id) {
    const contentElement = document.getElementById("content" + id);
    const displayArea = document.getElementById("displayArea");

  if (contentElement) {
      displayArea.innerHTML = contentElement.innerHTML;
      document.getElementById("fullscreenBox").classList.add("active");
    }
  }      