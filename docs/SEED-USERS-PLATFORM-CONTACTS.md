# 👥 Seed: Usuários com Contatos em Plataformas Externas

**Data**: 05/12/2025  
**Status**: ✅ Concluído

## 📊 Dados Criados

### **Usuários** (5)
| Username | Email | Plataformas |
|----------|-------|-------------|
| alice.silva | alice.silva@example.com | WhatsApp, Telegram |
| bob.santos | bob.santos@example.com | WhatsApp, Instagram |
| carlos.lima | carlos.lima@example.com | Telegram, Instagram |
| diana.costa | diana.costa@example.com | WhatsApp, Telegram, Instagram |
| eduardo.rocha | eduardo.rocha@example.com | WhatsApp |

### **Contatos por Plataforma** (10)

- **WhatsApp**: 4 contatos
  - Alice Silva: +5511987654321
  - Bob Santos: +5521976543210
  - Diana Costa: +5531965432109
  - Eduardo Rocha: +5541954321098

- **Telegram**: 3 contatos
  - Alice Silva: @alice_silva_tg (chatId: 123456789)
  - Carlos Lima: @carlos_lima_tg (chatId: 987654321)
  - Diana Costa: @diana_costa_tg (chatId: 456789123)

- **Instagram**: 3 contatos
  - Bob Santos: @bob.santos.ig (Business Account)
  - Carlos Lima: @carlos.lima.ig (Verified)
  - Diana Costa: @diana.costa.ig (Business + Verified)

---

## 🔍 Queries Úteis

### 1. **Listar todos os usuários**
```javascript
db.users.find({}, { username: 1, email: 1, active: 1 }).pretty()
```

### 2. **Listar todos os contatos**
```javascript
db.platformContacts.find({}, { 
  platform: 1, 
  platformUserId: 1, 
  displayName: 1, 
  userId: 1 
}).pretty()
```

### 3. **Buscar contatos de um usuário específico**
```javascript
const user = db.users.findOne({ username: "diana.costa" });
db.platformContacts.find({ userId: user._id }).pretty()
```

### 4. **Buscar usuário por contato de plataforma**
```javascript
// Por número WhatsApp
const contact = db.platformContacts.findOne({ 
  platform: "WHATSAPP", 
  platformUserId: "+5511987654321" 
});
db.users.findOne({ _id: contact.userId })

// Por username Telegram
const contact = db.platformContacts.findOne({ 
  platform: "TELEGRAM", 
  "metadata.username": "alice_silva_tg" 
});
db.users.findOne({ _id: contact.userId })

// Por username Instagram
const contact = db.platformContacts.findOne({ 
  platform: "INSTAGRAM", 
  "metadata.username": "bob.santos.ig" 
});
db.users.findOne({ _id: contact.userId })
```

### 5. **Contatos verificados por plataforma**
```javascript
// WhatsApp verificados
db.platformContacts.find({ 
  platform: "WHATSAPP", 
  "metadata.verified": true 
}).count()

// Instagram Business Accounts
db.platformContacts.find({ 
  platform: "INSTAGRAM", 
  "metadata.businessAccount": true 
})

// Telegram verificados
db.platformContacts.find({ 
  platform: "TELEGRAM", 
  "metadata.verified": true 
})
```

### 6. **Agregação: Usuários por número de plataformas**
```javascript
db.platformContacts.aggregate([
  {
    $group: {
      _id: "$userId",
      platforms: { $addToSet: "$platform" },
      count: { $sum: 1 }
    }
  },
  {
    $lookup: {
      from: "users",
      localField: "_id",
      foreignField: "_id",
      as: "userInfo"
    }
  },
  {
    $project: {
      username: { $arrayElemAt: ["$userInfo.username", 0] },
      platforms: 1,
      platformCount: "$count"
    }
  },
  {
    $sort: { platformCount: -1 }
  }
])
```

### 7. **Estatísticas por plataforma**
```javascript
db.platformContacts.aggregate([
  {
    $group: {
      _id: "$platform",
      total: { $sum: 1 },
      verified: {
        $sum: {
          $cond: [{ $eq: ["$metadata.verified", true] }, 1, 0]
        }
      },
      businessAccounts: {
        $sum: {
          $cond: [{ $eq: ["$metadata.businessAccount", true] }, 1, 0]
        }
      }
    }
  },
  {
    $sort: { total: -1 }
  }
])
```

---

## 🗑️ Limpeza de Dados

### **Remover todos os contatos**
```javascript
db.platformContacts.deleteMany({})
```

### **Remover todos os usuários**
```javascript
db.users.deleteMany({})
```

### **Remover contatos de uma plataforma específica**
```javascript
db.platformContacts.deleteMany({ platform: "WHATSAPP" })
```

### **Remover contatos de um usuário**
```javascript
const user = db.users.findOne({ username: "alice.silva" });
db.platformContacts.deleteMany({ userId: user._id })
```

---

## 📋 Índices Criados

```javascript
// Índice por userId
db.platformContacts.createIndex({ userId: 1 })

// Índice único por plataforma + platformUserId
db.platformContacts.createIndex(
  { platform: 1, platformUserId: 1 }, 
  { unique: true }
)

// Índice composto userId + platform
db.platformContacts.createIndex({ userId: 1, platform: 1 })
```

---

## 🔄 Re-executar Seed

### **Via PowerShell direto**
```powershell
# Criar usuários
$usersScript = "const users = [{_id: ObjectId('674f1a2b3c4d5e6f7a8b9001'),username: 'alice.silva',email: 'alice.silva@example.com',passwordHash: '`$2a`$10`$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',active: true,createdAt: new Date(),updatedAt: new Date()},{_id: ObjectId('674f1a2b3c4d5e6f7a8b9002'),username: 'bob.santos',email: 'bob.santos@example.com',passwordHash: '`$2a`$10`$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',active: true,createdAt: new Date(),updatedAt: new Date()},{_id: ObjectId('674f1a2b3c4d5e6f7a8b9003'),username: 'carlos.lima',email: 'carlos.lima@example.com',passwordHash: '`$2a`$10`$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',active: true,createdAt: new Date(),updatedAt: new Date()},{_id: ObjectId('674f1a2b3c4d5e6f7a8b9004'),username: 'diana.costa',email: 'diana.costa@example.com',passwordHash: '`$2a`$10`$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',active: true,createdAt: new Date(),updatedAt: new Date()},{_id: ObjectId('674f1a2b3c4d5e6f7a8b9005'),username: 'eduardo.rocha',email: 'eduardo.rocha@example.com',passwordHash: '`$2a`$10`$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',active: true,createdAt: new Date(),updatedAt: new Date()}]; db.users.deleteMany({}); db.users.insertMany(users);"

docker exec -i mongodb-dev mongosh --quiet chat_dev --eval $usersScript

# Criar contatos (comando muito longo - use via mongosh diretamente)
```

### **Via mongosh direto no container**
```bash
docker exec -it mongodb-dev mongosh chat_dev
```

Então copie e cole os scripts JavaScript diretamente.

---

## 🎯 Casos de Uso

### **Enviar mensagem para usuário via WhatsApp**
```javascript
// 1. Encontrar usuário
const user = db.users.findOne({ username: "alice.silva" });

// 2. Encontrar contato WhatsApp
const whatsappContact = db.platformContacts.findOne({
  userId: user._id,
  platform: "WHATSAPP"
});

// 3. Usar platformUserId para enviar mensagem
console.log("Enviar para: " + whatsappContact.platformUserId);
// Output: Enviar para: +5511987654321
```

### **Verificar se usuário tem Instagram**
```javascript
const user = db.users.findOne({ username: "carlos.lima" });
const hasInstagram = db.platformContacts.findOne({
  userId: user._id,
  platform: "INSTAGRAM"
}) !== null;

console.log("Tem Instagram? " + hasInstagram); // true
```

### **Listar todos os contatos de Business Accounts**
```javascript
db.platformContacts.find({
  "metadata.businessAccount": true
}).forEach(contact => {
  const user = db.users.findOne({ _id: contact.userId });
  print(`${user.username} - ${contact.platform}: ${contact.platformUserId}`);
});
```

---

## 📝 Notas

- **Password hash**: Todos os usuários têm senha `password` (bcrypt hash)
- **IDs fixos**: ObjectIds são fixos para facilitar testes
- **Metadados**: Cada plataforma tem metadados específicos (phoneNumber, chatId, username, etc.)
- **Active flag**: Todos os contatos estão ativos (`active: true`)
- **Timestamps**: `createdAt` e `updatedAt` são definidos com `new Date()`

---

## 🔗 Relacionamento de Dados

```
User (1) ──────< (N) PlatformContact
   ↓
  _id ←────── userId
```

Um usuário pode ter **vários contatos** em **diferentes plataformas**.
