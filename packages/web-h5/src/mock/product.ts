/**
 * 特色产品中心 mock 数据（H-05 静态阶段，后续 H-07 替换为 GET /products 接口）
 * - category：specialty 特产 / cultural 文创
 * - images：详情页轮播图（第一张为列表主图）
 * 与服务端测试数据一致，共 8 个商品（井冈山革命根据地区域 mock）
 */

export type ProductCategory = 'specialty' | 'cultural'

/** 特产 / 文创商品 */
export interface Product {
  id: number
  name: string
  /** 单价（元） */
  price: number
  description: string
  /** 列表主图 */
  image: string
  /** 详情页轮播图（含主图，至少 2 张） */
  images: string[]
  /** 当前库存（0 表示已售罄） */
  stock: number
  category: ProductCategory
  /** 角标文案，如 甄选 / 新品 */
  tag?: string
}

/**
 * 静态阶段商品图占位（文生图）；H-07 接入接口后替换为后端返回的 /uploads 地址
 * @param prompt 画面描述
 * @param size square 列表方图 / landscape 详情横图
 */
function productImage(
  prompt: string,
  size: 'square' | 'landscape' = 'square',
): string {
  const imageSize = size === 'square' ? 'square' : 'landscape_4_3'
  return `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=${encodeURIComponent(
    prompt,
  )}&image_size=${imageSize}`
}

export const products: Product[] = [
  {
    id: 1,
    name: '井冈山红米',
    price: 36,
    description:
      '井冈山高山梯田种植的红糙米，生长期长、米粒饱满、米香醇厚。当年红军在井冈山上"红米饭、南瓜汤"，红米由此成为承载红色记忆的粮食。真空袋装 1kg，蒸煮前建议浸泡 2 小时。',
    image: productImage('电商产品摄影，一袋真空包装的井冈山红米，露出红褐色米粒，木桌上摆放煮熟的红米饭，暖色调，简洁背景'),
    images: [
      productImage('电商横版产品图，真空袋装井冈山红米放在木桌上，旁边散落红褐色生米粒，自然光', 'landscape'),
      productImage('电商横版场景图，一碗蒸熟的红米饭配南瓜汤，粗陶碗与竹编背景，红色怀旧氛围', 'landscape'),
    ],
    stock: 200,
    category: 'specialty',
    tag: '甄选',
  },
  {
    id: 2,
    name: '井冈翠绿明前茶',
    price: 128,
    description:
      '海拔 800 米以上高山茶园明前采摘，一芽一叶，条索翠绿、清香持久、回味甘醇。礼盒内含 2 罐共 200g，附井冈山主题手提袋，适合自饮与馈赠。',
    image: productImage('电商产品摄影，红色主题茶叶礼盒，两罐井冈翠绿绿茶，旁边白瓷杯泡着嫩绿茶汤，简洁高级背景'),
    images: [
      productImage('电商横版产品图，井冈翠绿茶叶礼盒打开，两个绿色茶罐整齐摆放，配手提袋', 'landscape'),
      productImage('电商横版场景图，高山云雾茶园特写，嫩绿茶芽与采茶竹篓，清晨光线', 'landscape'),
    ],
    stock: 88,
    category: 'specialty',
    tag: '新品',
  },
  {
    id: 3,
    name: '井冈山野生笋干',
    price: 45,
    description:
      '选用井冈深山春笋，经去壳、蒸煮、炭火烘烤等传统工序制成，肉质厚实、鲜嫩爽口。泡发后可烧肉、炖鸡，是井冈山人家待客的常备山珍。袋装 250g。',
    image: productImage('电商产品摄影，竹篮里的井冈山笋干，黄褐色干笋片纹理清晰，旁边放竹筷和麻布，质朴背景'),
    images: [
      productImage('电商横版产品图，一盘泡发后的笋干特写，肉质肥厚，竹编盘与木桌背景', 'landscape'),
      productImage('电商横版场景图，春笋烧肉家常菜，热气腾腾，乡村木桌，暖色调', 'landscape'),
    ],
    stock: 6,
    category: 'specialty',
    tag: '热销',
  },
  {
    id: 4,
    name: '井冈原木香菇',
    price: 58,
    description:
      '井冈山椴木栽培香菇，自然风干锁鲜，菇盖厚实、菇香浓郁，煲汤炖鸡鲜香四溢。罐装 200g，密封防潮，附赠山珍食谱卡。',
    image: productImage('电商产品摄影，玻璃罐装干香菇，旁边散落几朵褐色厚肉香菇，木质背景，柔和光线'),
    images: [
      productImage('电商横版产品图，椴木香菇干菇特写，菇盖纹理清晰，竹篮与麻布背景', 'landscape'),
      productImage('电商横版场景图，砂锅里香菇炖鸡汤，汤色金黄，餐桌温馨氛围', 'landscape'),
    ],
    stock: 0,
    category: 'specialty',
  },
  {
    id: 5,
    name: '八角楼清油灯文创摆件',
    price: 68,
    description:
      '灵感源自茅坪八角楼那盏见证《中国的红色政权为什么能够存在？》诞生的清油灯。金属与树脂工艺还原灯盏造型，轻触可点亮暖光，配收藏卡与编号。',
    image: productImage('电商产品摄影，一盏复古清油灯文创摆件，铜色金属灯盏，暖光点亮，深色展台上的红色主题纪念品'),
    images: [
      productImage('电商横版产品图，清油灯文创摆件点亮暖光，旁边放收藏编号卡，深色背景高级感', 'landscape'),
      productImage('电商横版场景图，书桌上的清油灯摆件，旁边是八角楼旧居照片与旧书，怀旧氛围', 'landscape'),
    ],
    stock: 150,
    category: 'cultural',
    tag: '新品',
  },
  {
    id: 6,
    name: '红军灰帆布包',
    price: 59,
    description:
      '复刻行军挎包的灰绿色重磅帆布包，加厚纯棉帆布配铜扣，正面刺绣"为人民服务"字样。可斜挎可手提，日常通勤与红色研学都很合适。',
    image: productImage('电商产品摄影，灰绿色红军风格帆布挎包，铜扣和刺绣为人民服务字样，纯色简洁背景'),
    images: [
      productImage('电商横版产品图，灰绿帆布包平铺特写，铜扣、肩带与刺绣细节清晰', 'landscape'),
      productImage('电商横版场景图，年轻人斜挎红军帆布包走在井冈山老街，红色文旅氛围', 'landscape'),
    ],
    stock: 120,
    category: 'cultural',
    tag: '热销',
  },
  {
    id: 7,
    name: '朱毛会师纪念徽章套装',
    price: 48,
    description:
      '纪念 1928 年井冈山胜利会师，一套 3 枚金属珐琅徽章：会师桥、建军摇篮、井冈红旗。磨砂镀金工艺，配说明卡与收藏盒，别针磁吸两用。',
    image: productImage('电商产品摄影，三枚红色主题金属珐琅徽章摆放在收藏盒中，会师桥与红旗图案，镀金质感'),
    images: [
      productImage('电商横版产品图，三枚会师纪念徽章排列特写，珐琅填色与镀金细节，深色绒布背景', 'landscape'),
      productImage('电商横版场景图，收藏盒打开的徽章套装与说明卡，红色文创陈列，柔和光线', 'landscape'),
    ],
    stock: 96,
    category: 'cultural',
  },
  {
    id: 8,
    name: '红色足迹明信片套装',
    price: 29,
    description:
      '一套 10 张手绘水彩明信片，收录黄洋界、八角楼、会师广场等井冈山红色地标，背面印党史小故事。附赠同款主题贴纸与邮票位，适合研学打卡寄赠。',
    image: productImage('电商产品摄影，一摞手绘水彩风井冈山红色地标明信片，黄洋界与八角楼图案，麻绳捆扎'),
    images: [
      productImage('电商横版产品图，十张手绘明信片扇形展开，水彩风革命遗址插画，木桌背景', 'landscape'),
      productImage('电商横版场景图，一张明信片贴在旅行手账本上，旁边是钢笔与井冈山门票，文艺氛围', 'landscape'),
    ],
    stock: 300,
    category: 'cultural',
  },
]
