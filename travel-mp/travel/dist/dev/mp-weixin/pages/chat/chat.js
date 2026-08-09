"use strict";
const common_vendor = require("../../common/vendor.js");
const api_request = require("../../api/request.js");
const utils_websocket = require("../../utils/websocket.js");
const common_assets = require("../../common/assets.js");
const _sfc_main = {
  data() {
    return {
      currentTab: "chat",
      inputMessage: "",
      messages: [],
      sessions: [],
      isLoading: false,
      scrollTop: 0,
      scrollIntoView: "",
      userInfo: {},
      currentSessionId: null,
      // 入住人信息弹层（后端 interaction.type=guest_contact && status=waiting_user_contact 时打开）
      contactModalVisible: false,
      contactForm: {
        guestName: "",
        guestPhone: ""
      },
      contactError: "",
      pendingContact: null,
      // 行程信息
      travelInfo: {
        destination: "",
        startDate: "",
        days: 3
      }
    };
  },
  onLoad(options) {
    this.userInfo = common_vendor.index.getStorageSync("userInfo") || {};
    const travelInfo = common_vendor.index.getStorageSync("travelPlanInfo");
    if (travelInfo) {
      this.travelInfo = travelInfo;
    }
    if (options.sessionId) {
      this.currentSessionId = Number(options.sessionId);
      this.loadSession(options.sessionId);
    } else {
      this.loadLatestSession();
    }
  },
  onShow() {
    if (this.userInfo && this.userInfo.id) {
      this.connectWebSocket();
    }
  },
  onUnload() {
    this.disconnectWebSocket();
  },
  methods: {
    // ==================== WebSocket ====================
    connectWebSocket() {
      const userId = this.userInfo.id;
      if (!userId) return;
      utils_websocket.wsManager.addMessageHandler(this.handleWebSocketMessage);
      utils_websocket.wsManager.connect(userId);
    },
    disconnectWebSocket() {
      utils_websocket.wsManager.removeMessageHandler(this.handleWebSocketMessage);
      utils_websocket.wsManager.disconnect();
    },
    handleWebSocketMessage(data) {
      console.log("WebSocket 收到消息:", data);
      if (data.sessionId !== this.currentSessionId) {
        console.log("不是当前会话的消息，忽略");
        return;
      }
      let plan = null;
      if (data.planJson) {
        try {
          plan = JSON.parse(data.planJson);
        } catch (e) {
          plan = null;
        }
      }
      this.messages.push({
        role: "ASSISTANT",
        type: plan ? "plan" : "text",
        content: data.content,
        planJson: data.planJson,
        plan,
        interaction: data.interaction
      });
      this.checkContactInteraction();
      this.scrollToBottom();
    },
    // 扫描整个 messages 列表，决定是否打开入住人信息弹层
    // 规则：找到最后一条处于 waiting_user_contact 的 assistant 消息，
    //      且其后没有任何 user 消息提交过（即用户还没回应），才弹窗
    checkContactInteraction() {
      for (let i = this.messages.length - 1; i >= 0; i--) {
        const m = this.messages[i];
        if (m.role === "USER") {
          return;
        }
        if (m.role === "ASSISTANT") {
          const obj = this.parseInteraction(m.interaction);
          if (obj && obj.type === "guest_contact" && obj.status === "waiting_user_contact") {
            this.pendingContact = obj;
            this.contactForm = { guestName: "", guestPhone: "" };
            this.contactError = "";
            this.contactModalVisible = true;
          }
          return;
        }
      }
    },
    // 把 interaction 字段统一成对象（历史加载可能是 JSON 字符串）
    parseInteraction(raw) {
      if (!raw) return null;
      if (typeof raw === "object") return raw;
      if (typeof raw === "string") {
        try {
          return JSON.parse(raw);
        } catch (e) {
          return null;
        }
      }
      return null;
    },
    // ==================== Tab 切换 ====================
    switchTab(tab) {
      this.currentTab = tab;
      if (tab === "history") {
        this.loadSessions();
      }
    },
    // ==================== 行程规划 ====================
    showPlanForm() {
      common_vendor.index.navigateTo({
        url: "/pages/chat/plan-form"
      });
    },
    async startPlan() {
      if (!this.travelInfo.destination) {
        common_vendor.index.showToast({ title: "请输入目的地", icon: "none" });
        return;
      }
      if (!this.travelInfo.startDate) {
        common_vendor.index.showToast({ title: "请选择出发日期", icon: "none" });
        return;
      }
      const content = `我想去${this.travelInfo.destination}玩${this.travelInfo.days}天，出发日期是${this.travelInfo.startDate}`;
      this.inputMessage = content;
      this.sendMessage();
    },
    async loadSessions() {
      try {
        const userId = this.userInfo.id;
        const data = await api_request.getUserSessions(userId, 1, 10);
        console.log("获取用户的所有会话", data);
        if (data && data.records && data.records.length > 0) {
          this.sessions = data.records.map((item) => ({
            sessionId: item.sessionId,
            title: item.title,
            createTime: item.createTime
          }));
        }
      } catch (error) {
        console.error("加载会话失败:", error);
      }
    },
    async loadLatestSession() {
      try {
        const userId = this.userInfo.id;
        const data = await api_request.getUserSessions(userId, 1, 10);
        console.log("获取最近会话:", data);
        if (data && data.records && data.records.length > 0) {
          const latest = data.records[0];
          this.currentSessionId = latest.sessionId;
          this.loadSession(latest.sessionId);
        } else {
          this.messages = [];
        }
      } catch (error) {
        console.error("加载最近会话失败:", error);
        this.messages = [];
      }
    },
    async loadSession(sessionId) {
      try {
        const userId = this.userInfo.id;
        console.log("会话ID: ", sessionId);
        const data = await api_request.getSessionMessages(userId, sessionId, 1, 10);
        console.log("原始返回:", JSON.stringify(data, null, 2));
        console.log("消息: ", data.records);
        console.log("role值:", data.records.map((item) => item.role));
        if (data && data.records && data.records.length > 0) {
          this.messages = data.records.map((item) => {
            let plan = null;
            if (item.planJson) {
              try {
                plan = JSON.parse(item.planJson);
              } catch (e) {
                plan = null;
              }
            }
            return {
              msgId: item.msgId,
              role: item.role,
              content: item.content,
              planJson: item.planJson,
              plan,
              type: plan ? "plan" : "text",
              flowId: item.flowId,
              interaction: item.interaction
            };
          });
          this.checkContactInteraction();
        }
      } catch (error) {
        console.error("加载会话消息失败", error);
      }
    },
    openSession(session) {
      common_vendor.index.navigateTo({
        url: `/pages/chat/chat?sessionId=${session.sessionId}`
      });
    },
    async createNewSession() {
      common_vendor.index.showModal({
        title: "新建会话",
        editable: true,
        placeholderText: "请输入会话标题",
        success: async (res) => {
          if (res.confirm && res.content) {
            try {
              const userId = this.userInfo.id;
              const data = await api_request.createSession({ title: res.content });
              console.log("创建会话成功:", data);
              this.messages = [];
              this.currentTab = "chat";
              this.currentSessionId = data.sessionId;
              common_vendor.index.navigateTo({
                url: `/pages/chat/chat?sessionId=${data.sessionId}`
              });
            } catch (error) {
              console.error("创建会话失败:", error);
              common_vendor.index.showToast({ title: "创建会话失败", icon: "none" });
            }
          }
        }
      });
    },
    sendQuickQuestion(question) {
      this.inputMessage = question;
      this.sendMessage();
    },
    sendMessage() {
      if (!this.inputMessage.trim() || this.isLoading) return;
      if (!this.currentSessionId) {
        common_vendor.index.showToast({ title: "请先创建会话", icon: "none" });
        return;
      }
      this.sendContent(this.inputMessage.trim());
    },
    // 实际发送消息的公共逻辑，弹层表单和输入框都走这里
    sendContent(content) {
      if (!content) return;
      if (!this.currentSessionId) {
        common_vendor.index.showToast({ title: "请先创建会话", icon: "none" });
        return;
      }
      this.messages.push({
        role: "USER",
        type: "text",
        content
      });
      if (content === this.inputMessage.trim()) {
        this.inputMessage = "";
      }
      this.scrollToBottom();
      this.isLoading = true;
      const params = {
        sessionId: this.currentSessionId,
        role: "USER",
        content
      };
      if (this.travelInfo.startDate) {
        params.startDate = this.travelInfo.startDate;
      }
      if (this.travelInfo.days) {
        params.days = this.travelInfo.days;
      }
      console.log("请求参数:", params);
      api_request.sendMessage(params).then((res) => {
        console.log("消息发送成功:", res);
      }).catch((err) => {
        console.error("消息发送失败:", err);
        common_vendor.index.showToast({ title: "发送失败", icon: "none" });
        this.messages.pop();
      }).finally(() => {
        this.isLoading = false;
      });
    },
    // ==================== 入住人信息弹层 ====================
    submitContact() {
      const name = this.contactForm.guestName.trim();
      const phone = this.contactForm.guestPhone.trim();
      if (!name) {
        this.contactError = "请输入入住人姓名";
        return;
      }
      if (!/^[一-龥A-Za-z·]{2,20}$/.test(name)) {
        this.contactError = "姓名格式不正确（2-20 位中文/英文）";
        return;
      }
      if (!/^1[3-9]\d{9}$/.test(phone)) {
        this.contactError = "请输入正确的 11 位手机号";
        return;
      }
      this.contactError = "";
      this.contactModalVisible = false;
      const content = `入住人姓名：${name}，手机号：${phone}`;
      this.sendContent(content);
    },
    scrollToBottom() {
      this.$nextTick(() => {
        this.scrollIntoView = `msg-${this.messages.length - 1}`;
      });
    },
    formatTime(time) {
      if (!time) return "";
      const date = new Date(time);
      return `${date.getMonth() + 1}月${date.getDate()}日 ${date.getHours()}:${String(date.getMinutes()).padStart(2, "0")}`;
    },
    goBookHotel(destination) {
      common_vendor.index.switchTab({ url: "/pages/hotel/list" });
    }
  }
};
function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
  return common_vendor.e({
    a: $data.currentTab === "chat" ? 1 : "",
    b: common_vendor.o(($event) => $options.switchTab("chat")),
    c: $data.currentTab === "history" ? 1 : "",
    d: common_vendor.o(($event) => $options.switchTab("history")),
    e: common_vendor.o((...args) => $options.createNewSession && $options.createNewSession(...args)),
    f: $data.currentTab === "chat"
  }, $data.currentTab === "chat" ? common_vendor.e({
    g: $data.messages.length === 0
  }, $data.messages.length === 0 ? {
    h: common_assets._imports_0$2,
    i: common_vendor.o((...args) => $options.showPlanForm && $options.showPlanForm(...args)),
    j: common_vendor.o(($event) => $options.sendQuickQuestion("我想去北京玩3天，推荐一下行程")),
    k: common_vendor.o(($event) => $options.sendQuickQuestion("三亚5日游，预算5000元怎么安排")),
    l: common_vendor.o(($event) => $options.sendQuickQuestion("杭州适合周末亲子游吗"))
  } : common_vendor.e({
    m: common_vendor.f($data.messages, (msg, index, i0) => {
      return common_vendor.e({
        a: msg.role === "USER"
      }, msg.role === "USER" ? {
        b: $data.userInfo.avatar || "/static/images/avatar-default.png",
        c: common_vendor.t(msg.content)
      } : common_vendor.e({
        d: common_assets._imports_1$2,
        e: msg.type === "text"
      }, msg.type === "text" ? {
        f: common_vendor.t(msg.content)
      } : msg.type === "plan" ? common_vendor.e({
        h: common_vendor.t(msg.plan.title),
        i: common_vendor.t(msg.plan.destination),
        j: common_vendor.t(msg.plan.days),
        k: common_vendor.t(msg.plan.budget),
        l: common_vendor.f(msg.plan.daily_plans, (day, dIndex, i1) => {
          return common_vendor.e({
            a: common_vendor.t(day.day),
            b: common_vendor.t(day.theme),
            c: common_vendor.f(day.activities, (act, aIndex, i2) => {
              return common_vendor.e({
                a: common_vendor.t(act.time),
                b: common_vendor.t(act.name),
                c: common_vendor.t(act.description),
                d: act.location
              }, act.location ? {
                e: common_vendor.t(act.location)
              } : {}, {
                f: aIndex
              });
            }),
            d: day.estimated_cost
          }, day.estimated_cost ? {
            e: common_vendor.t(day.estimated_cost)
          } : {}, {
            f: dIndex
          });
        }),
        m: msg.plan.total_estimated_cost
      }, msg.plan.total_estimated_cost ? {
        n: common_vendor.t(msg.plan.total_estimated_cost)
      } : {}, {
        o: common_vendor.o(($event) => $options.goBookHotel(msg.plan.destination), index)
      }) : {}, {
        g: msg.type === "plan"
      }), {
        p: index,
        q: "msg-" + index
      });
    }),
    n: $data.isLoading
  }, $data.isLoading ? {
    o: common_assets._imports_1$2
  } : {}, {
    p: $data.scrollTop,
    q: $data.scrollIntoView
  }), {
    r: common_vendor.o((...args) => $options.sendMessage && $options.sendMessage(...args)),
    s: $data.inputMessage,
    t: common_vendor.o(($event) => $data.inputMessage = $event.detail.value),
    v: !$data.inputMessage || $data.isLoading,
    w: common_vendor.o((...args) => $options.sendMessage && $options.sendMessage(...args))
  }) : common_vendor.e({
    x: $data.sessions.length === 0
  }, $data.sessions.length === 0 ? {
    y: common_assets._imports_2$1
  } : {
    z: common_vendor.f($data.sessions, (session, k0, i0) => {
      return {
        a: common_vendor.t(session.title),
        b: common_vendor.t($options.formatTime(session.createTime)),
        c: session.id,
        d: common_vendor.o(($event) => $options.openSession(session), session.id)
      };
    })
  }), {
    A: $data.contactModalVisible
  }, $data.contactModalVisible ? common_vendor.e({
    B: $data.pendingContact
  }, $data.pendingContact ? {
    C: common_vendor.t($data.pendingContact.hotel_name || $data.pendingContact.hotelName)
  } : {}, {
    D: $data.contactForm.guestName,
    E: common_vendor.o(($event) => $data.contactForm.guestName = $event.detail.value),
    F: $data.contactForm.guestPhone,
    G: common_vendor.o(($event) => $data.contactForm.guestPhone = $event.detail.value),
    H: $data.contactError
  }, $data.contactError ? {
    I: common_vendor.t($data.contactError)
  } : {}, {
    J: common_vendor.o((...args) => $options.submitContact && $options.submitContact(...args))
  }) : {});
}
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["render", _sfc_render], ["__scopeId", "data-v-a041b13f"]]);
wx.createPage(MiniProgramPage);
